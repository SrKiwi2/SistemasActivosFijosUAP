package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linuxense.javadbf.*;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganismoFinImportService {
    
    private final IOrganismoFinancieroService organismoFinancieroService;

    @Data
    public static class ImportResult {
        private int totalFisicos, leidas, insertados, actualizados, marcadosBorrados, erroresLectura;
        private int omitidosCampos, erroresExcepcion;
        private List<String> errores = new ArrayList<>();
    }

    // OJO: sin @Transactional para no abortar todo por una fila mala
    public ImportResult importarOrganismoFin(MultipartFile dbfFile, Charset cs) throws IOException {
        ImportResult res = new ImportResult();

        try (InputStream in = new BufferedInputStream(dbfFile.getInputStream());
             DBFReader reader = new DBFReader(in, cs)) {

            // 1) Cabeceras
            int fc = reader.getFieldCount();
            StringBuilder sb = new StringBuilder("DBF FIELDS (ORGANISMO_FIN):\n");
            Map<String,Integer> idx = new HashMap<>();
            for (int i = 0; i < fc; i++) {
                DBFField f = reader.getField(i);
                String raw = f.getName();
                String key = normalize(raw);
                idx.put(key, i);
                sb.append(String.format("  %02d) name='%s' norm='%s' type=%s len=%d dec=%d%n",
                        i, raw, key, String.valueOf(f.getType()), f.getLength(), f.getDecimalCount()));
            }
            log.info(sb.toString());

            // 2) Índices
            int iGestion = pick(idx, "GESTION");
            int iOF      = pick(idx, "OF");
            int iDES     = pick(idx, "DES", "DESCRIPCION", "NOMBRE");
            int iSIGLA   = pick(idx, "SIGLA");

            res.setTotalFisicos(reader.getRecordCount());
            final boolean incluirBorrados = true;

            // 3) Lectura
            DBFRow r;
            while ((r = reader.nextRow()) != null) {
                try {
                    if (r.isDeleted() && !incluirBorrados) {
                        res.marcadosBorrados++;
                        continue;
                    }
                    res.leidas++;

                    Short gestion = bdToShort(r.getBigDecimal(iGestion), "GESTION", res.leidas, res);
                    String codOf  = limit(nvl(r.getString(iOF)), 20);
                    String des    = limit(nvl(r.getString(iDES)), 255);
                    String sigla  = limit(nvl(r.getString(iSIGLA)), 60);

                    if (gestion == null || isBlank(codOf) || isBlank(des)) {
                        res.omitidosCampos++;
                        res.errores.add(msgFila(res.leidas, "Campos incompletos: GESTION=" + gestion +
                                ", OF='" + codOf + "', DES='" + des + "'"));
                        continue;
                    }

                    // Upsert por (gestion, codOf). Fallback por (gestion, sigla).
                    OrganismoFinanciero of = organismoFinancieroService
                            .findByGestionAndCodOf(gestion, codOf)
                            .orElseGet(() -> {
                                if (!isBlank(sigla)) {
                                    return organismoFinancieroService
                                            .findByGestionAndSiglaIgnoreCase(gestion, sigla).orElse(null);
                                }
                                return null;
                            });

                    boolean nuevo = false;
                    if (of == null) {
                        of = new OrganismoFinanciero();
                        of.setGestion(gestion);
                        of.setCodOf(codOf);
                        nuevo = true;
                    } else {
                        // si vino por sigla pero codOf era distinto, actualizamos codOf
                        if (!isBlank(codOf)) of.setCodOf(codOf);
                    }

                    of.setDescripcion(des);
                    of.setSigla(sigla);
                    of.setEstado("ACTIVO"); // si tu AuditoriaConfig lo requiere

                    try {
                        organismoFinancieroService.save(of);
                        if (nuevo) res.insertados++; else res.actualizados++;
                    } catch (DataIntegrityViolationException dive) {
                        res.erroresExcepcion++;
                        res.errores.add(msgFila(res.leidas,
                                "Violación de integridad (¿duplicado uc (gestion,cod_of)?): " + root(dive) +
                                " [GESTION=" + gestion + ", OF=" + codOf + ", SIGLA=" + sigla + "]"));
                    }

                } catch (Exception exRow) {
                    res.erroresExcepcion++;
                    res.errores.add(msgFila(res.leidas, root(exRow)));
                }
            }
        }

        return res;
    }

    /* ===== helpers ===== */

    private String msgFila(int n, String m) { return "Fila " + n + ": " + m; }

    private String normalize(String raw) {
        if (raw == null) return "";
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "");
    }

    private int pick(Map<String,Integer> idx, String... names) {
        for (String n : names) {
            Integer i = idx.get(normalize(n));
            if (i != null) return i;
        }
        throw new IllegalArgumentException("Campo no encontrado: " + Arrays.toString(names));
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String nvl(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.matches("^[.\\-_\\s]{3,}$")) return null;
        return t;
    }

    private String limit(String s, int max) {
        if (s == null) return null;
        return (s.length() <= max) ? s : s.substring(0, max);
    }

    private Short bdToShort(BigDecimal bd, String campo, int fila, ImportResult res) {
        if (bd == null) return null;
        try {
            BigInteger bi = bd.toBigIntegerExact();
            int val = bi.intValueExact();
            if (val < Short.MIN_VALUE || val > Short.MAX_VALUE) {
                res.errores.add("Fila " + fila + ": " + campo + " fuera de rango (short): " + val + ", se deja nulo.");
                return null;
            }
            return (short) val;
        } catch (ArithmeticException ex) {
            res.errores.add("Fila " + fila + ": " + campo + " inválido '" + bd + "', se deja nulo.");
            return null;
        }
    }

    private String root(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) x = x.getCause();
        return x.getClass().getSimpleName() + ": " + String.valueOf(x.getMessage());
    }
}
