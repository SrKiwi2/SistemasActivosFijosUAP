package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFRow;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;

import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GrupoContableImportService {
    
    private final IGrupoContableService grupoContableService;

    @Data
    public static class ImportResult {
        private int totalFisicos;
        private int leidas;
        private int insertados;
        private int actualizados;
        private int marcadosBorrados;
        private int omitidosCampos;
        private int erroresLectura;
        private int erroresExcepcion;
        private List<String> errores = new ArrayList<>();
    }

    private static final Logger log = LoggerFactory.getLogger(GrupoContableImportService.class);

    @Transactional
    public ImportResult importarCodcont(MultipartFile dbfFile, Charset cs) throws IOException {
        ImportResult res = new ImportResult();

        try (InputStream in = new BufferedInputStream(dbfFile.getInputStream());
             DBFReader reader = new DBFReader(in, cs)) {

            // --- Cabeceras ---
            int fc = reader.getFieldCount();
            StringBuilder sb = new StringBuilder("CODCONT DBF FIELDS:\n");
            Map<String,Integer> idx = new HashMap<>();
            for (int i = 0; i < fc; i++) {
                var f = reader.getField(i);
                String raw = f.getName();
                String key = normalizeFieldName(raw);
                idx.put(key, i);
                sb.append(String.format("  %02d) name='%s' norm='%s' type=%s len=%d dec=%d%n",
                        i, raw, key, String.valueOf(f.getType()), f.getLength(), f.getDecimalCount()));
            }
            log.info(sb.toString());

            // --- Índices esperados ---
            int iCodCont   = pick(idx, "CODCONT");
            int iNombre    = pick(idx, "NOMBRE");
            int iVidaUtil  = pick(idx, "VIDAUTIL");
            int iObserv    = idx.getOrDefault("OBSERV", -1);    // (no lo mapeas en entidad)
            int iDepreciar = pick(idx, "DEPRECIAR");
            int iActualizar= pick(idx, "ACTUALIZAR");
            int iFeult     = idx.getOrDefault("FEULT", -1);     // (no lo mapeas en entidad)
            int iUsuar     = idx.getOrDefault("USUAR", -1);     // (no lo mapeas en entidad)

            res.setTotalFisicos(reader.getRecordCount());

            // --- Lectura robusta con DBFRow ---
            List<GrupoContable> batch = new ArrayList<>(500);
            final boolean incluirBorrados = true; // cámbialo si NO quieres filas marcadas como borradas

            DBFRow r;
            while ((r = reader.nextRow()) != null) {
                try {
                    if (r.isDeleted() && !incluirBorrados) {
                        res.marcadosBorrados++;
                        continue;
                    }
                    res.leidas++;

                    // Campos
                    Integer codCont   = bdToInteger(r.getBigDecimal(iCodCont), "CODCONT", res.leidas, res, Short.MIN_VALUE, Short.MAX_VALUE);
                    String  nombreRaw = r.getString(iNombre);
                    String  nombre    = nvl(nombreRaw);

                    Integer vidaUtil  = bdToInteger(r.getBigDecimal(iVidaUtil), "VIDAUTIL", res.leidas, res, Short.MIN_VALUE, Short.MAX_VALUE);
                    Boolean depreciar = asLogic(r, iDepreciar);
                    Boolean actualizar= asLogic(r, iActualizar);
                    // String observ   = iObserv>=0 ? nvl(r.getString(iObserv)) : null;
                    // Date feult     = iFeult>=0 ? r.getDate(iFeult) : null;
                    // String usuar   = iUsuar>=0 ? nvl(r.getString(iUsuar)) : null;

                    // Validación mínima
                    if (codCont == null && isBlank(nombre)) {
                        res.omitidosCampos++;
                        res.errores.add(msgFila(res.leidas, "Sin CODCONT y sin NOMBRE, se omite."));
                        continue;
                    }

                    // UPSERT por codContable o por nombre
                    GrupoContable gc = grupoContableService.findByCodContable(codCont)
                        .or(() -> isBlank(nombre) ? Optional.empty()
                                                  : grupoContableService.findFirstByNombreIgnoreCase(nombre))
                        .orElseGet(GrupoContable::new);

                    boolean nuevo = (gc.getIdGrupoContable() == null);

                    gc.setCodContable(codCont);
                    gc.setNombre(nombre);
                    gc.setVidaUtil(vidaUtil);
                    gc.setDepreciar(depreciar);
                    gc.setActualizar(actualizar);
                    gc.setEstado("ACTIVO");
                    // gc.setCodigo(...); // si tu fuente tuviera otro código interno

                    batch.add(gc);
                    if (nuevo) res.insertados++; else res.actualizados++;

                    if (batch.size() == 500) {
                        grupoContableService.saveAll(batch);
                        batch.clear();
                    }

                } catch (Exception exRow) {
                    res.erroresExcepcion++;
                    res.errores.add(msgFila(res.leidas, exRow.getMessage()));
                }
            }

            if (!batch.isEmpty()) {
                grupoContableService.saveAll(batch);
            }

            // (opcional) cuadrar conteos
            int resto = res.totalFisicos - (res.leidas + res.marcadosBorrados + res.erroresLectura);
            if (resto != 0) {
                log.debug("CODCONT conteo no cuadra: total={} leidas={} borrados={} errLectura={} resto={}",
                    res.totalFisicos, res.leidas, res.marcadosBorrados, res.erroresLectura, resto);
            }
        }

        return res;
    }

    /* ===== Helpers ===== */

    private String msgFila(int n, String m) { return "Fila " + n + ": " + m; }

    private String normalizeFieldName(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return key.replaceAll("[^A-Z0-9_]", "");
    }

    private int pick(Map<String,Integer> idx, String... names) {
        for (String n : names) {
            Integer i = idx.get(normalizeFieldName(n));
            if (i != null) return i;
        }
        throw new IllegalArgumentException("Campo no encontrado: " + Arrays.toString(names));
    }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String nvl(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private Integer bdToInteger(BigDecimal bd, String campo, int fila, ImportResult res, int min, int max) {
        if (bd == null) return null;
        try {
            BigInteger bi = bd.toBigIntegerExact(); // falla si tuviera decimales
            int val = bi.intValueExact();           // falla si no cabe en int
            if (val < min || val > max) {
                res.errores.add("Fila " + fila + ": " + campo + " fuera de rango (" + val + "), se deja nulo.");
                return null;
            }
            return val;
        } catch (ArithmeticException ex) {
            res.errores.add("Fila " + fila + ": " + campo + " inválido '" + bd + "', se deja nulo.");
            return null;
        }
    }

    private Boolean asLogic(DBFRow r, int idx) {
        if (idx < 0) return null;
        try {
            Boolean b = r.getBoolean(idx);
            if (b != null) return b;
        } catch (Exception ignore) {}
        String s = r.getString(idx);
        if (s == null) return null;
        s = s.trim().toUpperCase(Locale.ROOT);
        if (s.isEmpty()) return null;
        if (s.equals("T") || s.equals("TRUE") || s.equals("Y") || s.equals("S") || s.equals("1")) return true;
        if (s.equals("F") || s.equals("FALSE") || s.equals("N") || s.equals("0")) return false;
        return null;
    }
}