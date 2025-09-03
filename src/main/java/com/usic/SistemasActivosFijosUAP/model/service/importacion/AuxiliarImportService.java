package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFRow;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuxiliarImportService {
    
    private final IEntidadService entidadService;
    private final IPredioServicio predioServicio;
    private final IGrupoContableService grupoContableService;
    private final IAuxiliarService auxiliarService;

    @Data
    public static class ImportResult {
        private int totalFisicos, leidas, insertados, actualizados, marcadosBorrados, erroresLectura;
        private int omitidosCampos, omitidosSinEntidad, omitidosSinPredio, omitidosSinGrupo, erroresExcepcion;
        private List<String> errores = new ArrayList<>();
    }

    // OJO: sin @Transactional aquí
    public ImportResult importarAuxiliar(MultipartFile dbfFile, Charset cs, Short gestionPreferida) throws IOException {
        ImportResult res = new ImportResult();

        try (InputStream in = new BufferedInputStream(dbfFile.getInputStream());
             DBFReader reader = new DBFReader(in, cs)) {

            // 1) Cabeceras
            int fc = reader.getFieldCount();
            StringBuilder sb = new StringBuilder("DBF FIELDS (AUXILIAR):\n");
            Map<String,Integer> idx = new HashMap<>();
            for (int i = 0; i < fc; i++) {
                DBFField f = reader.getField(i);
                String raw = f.getName();
                String key = normalizeFieldName(raw);
                idx.put(key, i);
                sb.append(String.format("  %02d) name='%s' norm='%s' type=%s len=%d dec=%d%n",
                        i, raw, key, String.valueOf(f.getType()), f.getLength(), f.getDecimalCount()));
            }
            log.info(sb.toString());

            // 2) Índices
            int iEntidad   = pick(idx, "ENTIDAD", "ENT");
            int iUnidad    = pick(idx, "UNIDAD", "UNI");
            int iCodCont   = pick(idx, "CODCONT", "COD_CONT");
            int iCodAux    = pick(idx, "CODAUX", "COD_AUX");
            int iNomAux    = pick(idx, "NOMAUX", "NOM_AUX", "NOMBRE");
            int iObserv    = pick(idx, "OBSERV", "OBS");
            int iFeult     = pick(idx, "FEULT", "FECHA", "F_ULT");
            int iUsuario   = pick(idx, "USUAR", "USUARIO");

            res.setTotalFisicos(reader.getRecordCount());

            final boolean incluirBorrados = true;
            final boolean useUC3Cols = false; // true => UC (predio, grupo, codAux)

            // 3) Lectura fila a fila
            DBFRow r;
            while ((r = reader.nextRow()) != null) {
                try {
                    if (r.isDeleted() && !incluirBorrados) {
                        res.marcadosBorrados++;
                        continue;
                    }
                    res.leidas++;

                    String entidadCodRaw = nvl(r.getString(iEntidad));
                    String unidadRaw     = nvl(r.getString(iUnidad));
                    Short  codCont       = bdToShort(r.getBigDecimal(iCodCont), "CODCONT", res.leidas, res);
                    Short  codAux        = bdToShort(r.getBigDecimal(iCodAux),  "CODAUX",  res.leidas, res);
                    String nomAux        = limit(nvl(r.getString(iNomAux)), 255);
                    String observ        = nvl(r.getString(iObserv));
                    LocalDate fechaUlt   = toLocalDate(r.getDate(iFeult));
                    String usuario       = limit(nvl(r.getString(iUsuario)), 60);

                    String entidadCod = norm(entidadCodRaw);
                    String unidad     = norm(unidadRaw);

                    // Validación mínima
                    if (isBlank(entidadCod) || isBlank(unidad) || codCont == null || codAux == null) {
                        res.omitidosCampos++;
                        res.errores.add(msgFila(res.leidas,
                                "ENTIDAD/UNIDAD/CODCONT/CODAUX incompletos. ENTIDAD='" + entidadCodRaw +
                                        "', UNIDAD='" + unidadRaw + "', CODCONT=" + codCont + ", CODAUX=" + codAux));
                        continue;
                    }

                    // Entidad
                    Entidad entidad = (gestionPreferida != null)
                            ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, entidadCod).orElse(null)
                            : entidadService.findTopByEntidadCodigoOrderByGestionDesc(entidadCod).orElse(null);
                    if (entidad == null) {
                        res.omitidosSinEntidad++;
                        res.errores.add(msgFila(res.leidas, "ENTIDAD código " + entidadCod + " no encontrada."));
                        continue;
                    }

                    // Predio
                    Predio predio = predioServicio.findByEntidadAndUnidadIgnoreCase(entidad, unidad).orElse(null);
                    if (predio == null) {
                        res.omitidosSinPredio++;
                        res.errores.add(msgFila(res.leidas, "Predio no encontrado para ENTIDAD=" + entidadCod +
                                " UNIDAD=" + unidad));
                        continue;
                    }

                    // Grupo contable
                    GrupoContable gc = grupoContableService.findByCodContable(Integer.valueOf(codCont)).orElse(null);
                    if (gc == null) {
                        res.omitidosSinGrupo++;
                        res.errores.add(msgFila(res.leidas, "GrupoContable no encontrado para CODCONT=" + codCont));
                        continue;
                    }

                    // Upsert Auxiliar
                    Auxiliar aux = (useUC3Cols)
                            ? auxiliarService.findByPredioAndGrupoContableAndCodAux(predio, gc, codAux).orElse(null)
                            : auxiliarService.findByPredioAndCodAux(predio, codAux).orElse(null);

                    boolean nuevo = false;
                    if (aux == null) {
                        aux = new Auxiliar();
                        aux.setPredio(predio);
                        aux.setGrupoContable(gc);
                        aux.setCodAux(codAux);
                        nuevo = true;
                    }

                    aux.setNombre(isBlank(nomAux) ? ("AUX " + codAux) : nomAux);
                    aux.setObserv(observ);
                    aux.setFechaUlt(fechaUlt);
                    aux.setUsuario(usuario);
                    aux.setEstado("ACTIVO"); // importante si _estado es NOT NULL

                    // Guarda por fila y captura violaciones (UNIQUE/FK/NOT NULL)
                    try {
                        auxiliarService.save(aux); // una mini-TX por fila si tu service es @Transactional, quita eso.
                        if (nuevo) res.insertados++; else res.actualizados++;
                    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                        res.erroresExcepcion++;
                        res.errores.add(msgFila(res.leidas,
                                "Violación de integridad (posible duplicado uk_aux_predio_codaux o NOT NULL/FK). " +
                                detalleFila(entidadCod, unidad, codCont, codAux) + " -> " + rootCause(ex)));
                        // seguimos con la siguiente fila
                    }

                } catch (Exception exRow) {
                    res.erroresExcepcion++;
                    res.errores.add(msgFila(res.leidas, rootCause(exRow)));
                }
            }

            // chequeo simple de conteo
            int resto = res.totalFisicos - (res.leidas + res.marcadosBorrados + res.erroresLectura);
            if (resto != 0) {
                log.debug("AUX DBF conteo: total={} leidas={} borrados={} errLectura={} resto={}",
                        res.totalFisicos, res.leidas, res.marcadosBorrados, res.erroresLectura, resto);
            }
        }

        return res;
    }

    /* ==== helpers ==== */

    private String msgFila(int n, String m) { return "Fila " + n + ": " + m; }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private String nvl(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        if (t.matches("^[.\\-_\\s]{3,}$")) return null;
        return t;
    }

    private String norm(String s) { return isBlank(s) ? null : s.trim().toUpperCase(Locale.ROOT); }

    private String limit(String s, int max) { return (s == null || s.length() <= max) ? s : s.substring(0, max); }

    private LocalDate toLocalDate(Date d) { return (d == null) ? null : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate(); }

    private Short bdToShort(BigDecimal bd, String campo, int fila, ImportResult res) {
        if (bd == null) return null;
        try {
            BigInteger bi = bd.toBigIntegerExact();
            int val = bi.intValueExact();
            if (val < Short.MIN_VALUE || val > Short.MAX_VALUE) {
                res.getErrores().add("Fila " + fila + ": " + campo + " fuera de rango (short): " + val + ", se deja nulo.");
                return null;
            }
            return (short) val;
        } catch (ArithmeticException ex) {
            res.getErrores().add("Fila " + fila + ": " + campo + " inválido '" + bd + "', se deja nulo.");
            return null;
        }
    }

    private int pick(Map<String,Integer> idx, String... names) {
        for (String n : names) {
            Integer i = idx.get(normalizeFieldName(n));
            if (i != null) return i;
        }
        throw new IllegalArgumentException("Campo no encontrado: " + Arrays.toString(names));
    }

    private String normalizeFieldName(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toUpperCase(Locale.ROOT);
        return key.replaceAll("[^A-Z0-9_]", "");
    }

    private String rootCause(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null) x = x.getCause();
        return x.getClass().getSimpleName() + ": " + String.valueOf(x.getMessage());
    }

    private String detalleFila(String ent, String uni, Short codCont, Short codAux) {
        return "[ENTIDAD=" + ent + ", UNIDAD=" + uni + ", CODCONT=" + codCont + ", CODAUX=" + codAux + "]";
    }
}
