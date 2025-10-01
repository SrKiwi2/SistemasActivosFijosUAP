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
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFRow;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResponsableImportService {

    private final IOficinaService oficinaService;
    private final IPersonaService personaService;
    private final ICargoService cargoService;
    private final IResponsableService responsableService;

    @Data
    public static class ImportResult {
        private int totalFisicos;
        private int leidas;
        private int insertados;
        private int actualizados;
        private int marcadosBorrados;
        private int omitidosCampos;
        private int omitidosSinEntidad;
        private int omitidosSinPredio;
        private int omitidosSinOficina;
        private int erroresExcepcion;
        private int erroresLectura;
        private List<String> errores = new ArrayList<>();
    }

    private static final Logger log = LoggerFactory.getLogger(ResponsableImportService.class);

    @Transactional
    public ImportResult importarResponsable(MultipartFile dbfFile, Charset cs, Short gestionPreferida)
            throws IOException {

        ImportResult res = new ImportResult();

        try (InputStream in = new BufferedInputStream(dbfFile.getInputStream());
             DBFReader reader = new DBFReader(in, cs)) {

            // 1) Cabeceras (log, no al JSON)
            int fc = reader.getFieldCount();
            StringBuilder sb = new StringBuilder("DBF FIELDS:\n");
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < fc; i++) {
                var f = reader.getField(i);
                String raw = f.getName();
                String key = normalizeFieldName(raw);
                idx.put(key, i);
                sb.append(String.format(
                    "  %02d) name='%s' norm='%s' type=%s length=%d decimals=%d%n",
                    i, raw, key, String.valueOf(f.getType()), f.getLength(), f.getDecimalCount()
                ));
            }
            log.info(sb.toString());

            // 2) Índices por nombre (cabeceras coinciden con el archivo que mostraste)
            int iEntidad   = pick(idx, "ENTIDAD", "ENT");
            int iUnidad    = pick(idx, "UNIDAD", "UNI");
            int iCodOfic   = pick(idx, "CODOFIC", "CODOFI", "COD_OFI");
            int iCodResp   = pick(idx, "CODRESP", "COD_RESP");
            int iNomResp   = pick(idx, "NOMRESP", "NOMBRE", "RESP");
            int iCargo     = pick(idx, "CARGO");
            int iObserv    = pick(idx, "OBSERV", "OBS");
            int iCi        = pick(idx, "CI", "CEDULA");
            int iFeult     = pick(idx, "FEULT", "FECHA", "F_ULT");
            int iUsuario   = pick(idx, "USUAR", "USUARIO");
            int iCodExp    = pick(idx, "COD_EXP", "CODEXP");
            int iApiEstado = pick(idx, "API_ESTADO", "APIESTADO");

            // 3) Métricas de conteo
            res.setTotalFisicos(reader.getRecordCount());

            // Si quieres “importarlo TODO” (no omitir por maestro faltante), pon esto en true
            final boolean crearFaltantes = false;
            final boolean incluirBorrados = true; // <-- cámbialo a voluntad

            // 4) Lectura segura con while (evita rows == null)
            List<Responsable> batch = new ArrayList<>(1000);
            Map<String, Persona> cachePersonaPorCI = new HashMap<>(4000);      // ci -> Persona
            Map<String, Persona> cachePersonaPorNombre = new HashMap<>(8000);  // canon -> Persona

            DBFRow r;
            res.setTotalFisicos(reader.getRecordCount()); 
            while ((r = reader.nextRow()) != null) {

                try {
                    // Si el registro está marcado como borrado...
                    if (r.isDeleted()) {
                        if (!incluirBorrados) {
                            res.marcadosBorrados++;  // los contamos y seguimos
                            continue;
                        }
                        // incluirBorrados=true => lo procesamos normal
                    }
                    res.leidas++;

                    // --- Campos DBF
                    String entidadCodRaw = r.getString(iEntidad);
                    String unidadRaw     = r.getString(iUnidad);

                    Short codOfi = bdToShort(r.getBigDecimal(iCodOfic), "CODOFIC", res.leidas, res);
                    String codRespTxt = nvl(r.getString(iCodResp));
                    String nomResp    = nvl(r.getString(iNomResp));
                    String cargoTxt   = nvl(r.getString(iCargo));
                    String observ     = nvl(r.getString(iObserv));
                    String ci         = nvl(r.getString(iCi));

                    Date d  = r.getDate(iFeult);
                    LocalDate feult      = (d == null ? null : d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());

                    String usuario    = nvl(r.getString(iUsuario));
                    Short codExp      = bdToShort(r.getBigDecimal(iCodExp), "COD_EXP", res.leidas, res);
                    Short apiEstado   = bdToShort(r.getBigDecimal(iApiEstado), "API_ESTADO", res.leidas, res);

                    String entidadCod = norm(entidadCodRaw);
                    String unidad     = norm(unidadRaw);

                    // Validación mínima
                    if (isBlank(entidadCod) || isBlank(unidad) || codOfi == null) {
                        res.omitidosCampos++;
                        res.errores.add(msgFila(res.leidas,
                            "ENTIDAD/UNIDAD/CODOFIC incompletos. ENTIDAD='" + entidadCodRaw +
                            "', UNIDAD='" + unidadRaw + "', CODOFI=" + codOfi));
                        continue;
                    }

                    Optional<Oficina> oficinaEncontrada = oficinaService.findByUnidadAndCodOfi(unidad, codOfi);

                    // Persona (upsert por CI si hay; si no, creamos)
                    String canon = canonNombre(nomResp);
                    Persona persona = null;

                    if (!isBlank(ci)) {
                        persona = cachePersonaPorCI.get(ci);
                        if (persona == null) {
                            persona = personaService.findFirstByCi(ci).orElse(null);
                            if (persona != null) cachePersonaPorCI.put(ci, persona);
                        }
                    }

                    if (persona == null) {
                        if (canon != null) {
                            // 1) intenta reutilizar en el MISMO archivo (evita crear varias iguales)
                            persona = cachePersonaPorNombre.get(canon);
                            // 2) opcional: intentar en BD (cuidado con homónimos)
                            if (persona == null) {
                                // List<Persona> hits = personaService.findAllByNombreCanonico(canon); // si implementas el repo
                                // if (!hits.isEmpty()) persona = hits.get(0);
                            }
                        }
                    }

                    if (persona == null) {
                        // crear
                        String[] np = splitNombrePersona(nomResp);
                        String pNombre  = clip(np[0], 120);
                        String pPaterno = clip(np[1], 60);
                        String pMaterno = clip(np[2], 60);

                        persona = new Persona();
                        persona.setNombre(pNombre);
                        persona.setPaterno(pPaterno);
                        persona.setMaterno(pMaterno);
                        persona.setCi(isBlank(ci) ? null : ci.trim());
                        persona.setEstado("ACTIVO");
                        personaService.save(persona);
                    }

                    if (canon != null) cachePersonaPorNombre.putIfAbsent(canon, persona);

                    // Cargo (upsert por nombre, si viene)
                    Cargo cargo = null;
                    if (!isBlank(cargoTxt)) {
                        String cargoNom = trimMax(cargoTxt, 120);
                        cargo = cargoService.findFirstByNombreIgnoreCase(cargoNom).orElseGet(() -> {
                            Cargo c = new Cargo();
                            c.setNombre(cargoNom);
                            c.setDescripcion(null);
                            c.setEstado("ACTIVO");
                            return cargoService.save(c);
                        });
                    }

                    Oficina oficina = oficinaEncontrada.orElse(null);

                    // Upsert Responsable
                    Responsable resp = null;
                    if (!isBlank(codRespTxt)) {
                        // Si hay CODORESP, la única llave de upsert es (oficina, codResp).
                        // NO hacer fallback por persona en este caso.
                        resp = responsableService
                                .findByOficinaAndCodigoFuncionario(oficina, codRespTxt.trim())
                                .orElse(null);
                    } else {
                        // Sin CODORESP, usa (oficina, persona)
                        resp = responsableService.findByOficinaAndPersona(oficina, persona).orElse(null);
                    }

                    if (resp == null) {
                        resp = new Responsable();
                        resp.setOficina(oficina);
                        resp.setPersona(persona);
                    }

                    boolean nuevo = (resp.getIdResponsable() == null);

                    resp.setCodigoFuncionario(isBlank(codRespTxt) ? null : trimMax(codRespTxt, 60));
                    resp.setCargo(cargo);
                    resp.setObserv(nvl(observ));
                    resp.setFechaUlt(feult);
                    resp.setUsuario(nvl(usuario));
                    resp.setCodExp(codExp);
                    resp.setApiEstado(apiEstado);
                    resp.setEstado("ACTIVO");

                    try {
                        responsableService.save(resp);
                        if (nuevo) res.insertados++; else res.actualizados++;
                    } catch (DataIntegrityViolationException e) {
                        res.erroresExcepcion++;
                        res.errores.add(msgFila(res.leidas, "Integridad: " + e.getMostSpecificCause().getMessage()));
                    }

                } catch (Exception exRow) {
                    res.erroresExcepcion++;
                    res.errores.add(msgFila(res.leidas, exRow.getMessage()));
                }
            }

            // flush final
            if (!batch.isEmpty()) {
                responsableService.saveAll(batch);
            }

            // marcados borrados (aprox)
            int resto = res.totalFisicos - (res.leidas + res.marcadosBorrados + res.erroresLectura);
            if (resto != 0) {
                log.debug("DBF conteo no cuadra: total={} leidas={} borrados={} errLectura={} resto={}",
                        res.totalFisicos, res.leidas, res.marcadosBorrados, res.erroresLectura, resto);
            }
        }
        return res;
    }

    /* ===== Helpers ===== */

    private String canonNombre(String s) {
        if (s == null) return null;
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String msgFila(int n, String m) { return "Fila " + n + ": " + m; }

    private boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }

    private boolean isBlankLike(String s) {
        if (s == null) return true;
        String t = s.trim();
        if (t.isEmpty()) return true;
        // "ruido": solo ., _ , - o espacios (>=3)
        return t.matches("^[.\\-_\\s]{3,}$");
    }

    private String nvl(String s)  { return isBlankLike(s) ? null : s.trim(); }

    private String norm(String s) { return isBlankLike(s) ? null : s.trim().toUpperCase(); }

    private String trimMax(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max);
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

    private int pick(Map<String, Integer> idx, String... names) {
        for (String n : names) {
            Integer i = idx.get(normalizeFieldName(n));
            if (i != null) return i;
        }
        throw new IllegalArgumentException("Campo no encontrado: " + Arrays.toString(names));
    }

    private String normalizeFieldName(String raw) {
        if (raw == null) return "";
        String key = raw.trim().toUpperCase(Locale.ROOT);
        key = key.replaceAll("[^A-Z0-9_]", "");
        return key;
    }

    // ----- Helpers de nombre -----
    private String[] splitNombrePersona(String full) {
        // Devuelve [nombre, paterno, materno]
        String[] res = new String[]{null, null, null};
        if (isBlankLike(full)) {
            res[0] = "SIN NOMBRE";
            return res;
        }
        // normaliza espacios
        String s = full.trim().replaceAll("\\s+", " ");
        String[] tok = s.split(" ");
        int n = tok.length;

        if (n == 1) {
            res[0] = tok[0];
        } else if (n == 2) {
            res[0] = tok[0];
            res[1] = tok[1];
        } else {
            // n >= 3 => nombre = todas menos las 2 últimas
            res[0] = String.join(" ", java.util.Arrays.copyOfRange(tok, 0, n - 2));
            res[1] = tok[n - 2];
            res[2] = tok[n - 1];
        }
        return res;
    }

    private String clip(String s, int max) {
        if (s == null) return null;
        s = s.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }
}