package com.usic.SistemasActivosFijosUAP.interoperabilidad;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linuxense.javadbf.DBFDataType;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFWriter;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ActivoDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.AuxiliarDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.EntidadDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.GrupoContableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.OficinaDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.OrganismoFinDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ResponsableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SolTransferenciaDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.UnidadAdminDbf;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public class JavaDbfService {

    private record DbfFieldMeta(
        String name,
        char   type,
        int    recOffset,   // posición dentro del registro (1-based, tras flag de borrado)
        int    length
    ) {}

    private record DbfMeta(
        int                 recordCount,
        int                 headerSize,
        int                 recordSize,
        List<DbfFieldMeta>  fields
    ) {
        DbfFieldMeta field(String name) {
            return fields.stream()
                .filter(f -> f.name().equalsIgnoreCase(name.trim()))
                .findFirst().orElse(null);
        }
    }

    private final Path baseDir;
    private final String charset;
    private final Object codcontLock = new Object();
    private final Object oficinaLock = new Object();

    public JavaDbfService(Path baseDir, String charset) {
        this.baseDir = baseDir;
        this.charset = charset;
    }

    private static final Logger log = LoggerFactory.getLogger(JavaDbfService.class);

    // ==== LECTOR DE CODCONT,BDF
    public List<GrupoContableDbf> listarCodcontAll(String filtroTexto) throws Exception {
        Path file = baseDir.resolve("CODCONT.DBF");
        List<GrupoContableDbf> out = new ArrayList<>();

        Charset cs = (charset != null && !charset.isBlank())
                ? Charset.forName(charset)
                : StandardCharsets.UTF_8;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in, cs)) {

            int idxCODCONT = -1, idxNOMBRE = -1, idxVIDAUTIL = -1, idxDEPRECIAR = -1, idxACTUALIZAR = -1;
            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "CODCONT" -> idxCODCONT = i;
                    case "NOMBRE" -> idxNOMBRE = i;
                    case "VIDAUTIL" -> idxVIDAUTIL = i;
                    case "DEPRECIAR" -> idxDEPRECIAR = i;
                    case "ACTUALIZAR" -> idxACTUALIZAR = i;
                }
            }

            final String q = filtroTexto == null ? null : filtroTexto.toLowerCase(Locale.ROOT);
            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                String nom = asString(row, idxNOMBRE);
                if (q != null && (nom == null || !nom.toLowerCase(Locale.ROOT).contains(q)))
                    continue;

                Long cod = asLong(row, idxCODCONT);
                Integer vida = asInt(row, idxVIDAUTIL);
                Boolean dep = asBool(row, idxDEPRECIAR);
                Boolean act = asBool(row, idxACTUALIZAR);

                out.add(GrupoContableDbf.builder()
                        .codContable(cod)
                        .nombre(nom)
                        .vidaUtil(vida)
                        .depreciar(dep)
                        .actualizar(act)
                        .idGrupoContable(cod)
                        .build());
            }
        }
        return out;
    }

    // ===== ENTIDADES.DBF
    public List<EntidadDbf> listarEntidadesAll(Short gestionFiltro, String q) throws Exception {

        Path file = baseDir.resolve("entidades.DBF");
        List<EntidadDbf> out = new ArrayList<>();

        Charset cs = (charset != null && !charset.isBlank())
                ? Charset.forName(charset)
                : StandardCharsets.UTF_8;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in, cs)) {

            int idxGESTION = -1, idxENTIDAD = -1, idxDESC = -1, idxSIGLA = -1,
                    idxSECTOR = -1, idxSUBSEC = -1, idxAREA = -1, idxSUBAREA = -1, idxNIVEL = -1;

            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "GESTION" -> idxGESTION = i;
                    case "ENTIDAD" -> idxENTIDAD = i;
                    case "DESC_ENT" -> idxDESC = i;
                    case "SIGLA_ENT" -> idxSIGLA = i;
                    case "SECTOR_ENT" -> idxSECTOR = i;
                    case "SUBSEC_ENT" -> idxSUBSEC = i;
                    case "AREA_ENT" -> idxAREA = i;
                    case "SUBAREAENT" -> idxSUBAREA = i;
                    case "NIVEL_INST" -> idxNIVEL = i;
                }
            }

            final String ql = (q == null ? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                Short gestion = asInt(row, idxGESTION) == null ? null : asInt(row, idxGESTION).shortValue();
                String codigo = asString(row, idxENTIDAD);
                String desc = asString(row, idxDESC);
                String sigla = asString(row, idxSIGLA);

                if (gestionFiltro != null && !gestionFiltro.equals(gestion))
                    continue;
                if (ql != null) {
                    String hay = (desc != null ? desc.toLowerCase(Locale.ROOT) : "") +
                            " " + (sigla != null ? sigla.toLowerCase(Locale.ROOT) : "") +
                            " " + (codigo != null ? codigo.toLowerCase(Locale.ROOT) : "");
                    if (!hay.contains(ql))
                        continue;
                }

                out.add(EntidadDbf.builder()
                        .gestion(gestion)
                        .entidadCodigo(codigo)
                        .descripcion(desc)
                        .sigla(sigla)
                        .sectorEnt(asInt(row, idxSECTOR) == null ? null : asInt(row, idxSECTOR).shortValue())
                        .subsecEnt(asInt(row, idxSUBSEC) == null ? null : asInt(row, idxSUBSEC).shortValue())
                        .areaEnt(asInt(row, idxAREA) == null ? null : asInt(row, idxAREA).shortValue())
                        .subareaEnt(asInt(row, idxSUBAREA) == null ? null : asInt(row, idxSUBAREA).shortValue())
                        .nivelInst(asInt(row, idxNIVEL) == null ? null : asInt(row, idxNIVEL).shortValue())
                        .build());
            }
        }
        return out;
    }

    // ==== UNIDADADMIN.DBF
    public List<UnidadAdminDbf> listarUnidadAdminAll(String q) throws Exception {
        Path file = baseDir.resolve("unidadadmin.DBF");
        List<UnidadAdminDbf> out = new ArrayList<>();

        Charset cs = (charset != null && !charset.isBlank())
                ? Charset.forName(charset)
                : StandardCharsets.UTF_8;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in, cs)) {

            int idxENTIDAD = -1, idxUNIDAD = -1, idxDESCRIP = -1, idxCIUDAD = -1, idxESTADO = -1;

            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "ENTIDAD" -> idxENTIDAD = i;
                    case "UNIDAD" -> idxUNIDAD = i;
                    case "DESCRIP" -> idxDESCRIP = i;
                    case "CIUDAD" -> idxCIUDAD = i;
                    case "ESTADOUNI" -> idxESTADO = i;
                }
            }

            final String ql = (q == null ? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                String entidad = asString(row, idxENTIDAD);
                String unidad = asString(row, idxUNIDAD);
                String descrip = asString(row, idxDESCRIP);
                String ciudad = asString(row, idxCIUDAD);
                Short estado = asInt(row, idxESTADO) == null ? null : asInt(row, idxESTADO).shortValue();

                if (ql != null) {
                    String hay = ((entidad == null ? "" : entidad) + " " + (unidad == null ? "" : unidad) + " "
                            + (descrip == null ? "" : descrip) + " " + (ciudad == null ? "" : ciudad))
                            .toLowerCase(Locale.ROOT);
                    if (!hay.contains(ql))
                        continue;
                }

                if ((entidad == null || entidad.isBlank()) || (unidad == null || unidad.isBlank())) {
                    continue;
                }

                out.add(UnidadAdminDbf.builder()
                        .entidadCodigo(entidad)
                        .unidad(unidad)
                        .descrip(descrip)
                        .ciudad(ciudad)
                        .estadoUni(estado)
                        .build());
            }
        }
        return out;
    }

    // ===== OFICINA.DBF
    public List<OficinaDbf> listarOficinaAll(String q) {
        Path file = baseDir.resolve("OFICINA.DBF");
        List<OficinaDbf> out = new ArrayList<>();

        if (!Files.exists(file)) return out;

        Charset cs = (charset != null && !charset.isBlank()) ? Charset.forName(charset) : Charset.forName("CP1252");

        try (InputStream in = Files.newInputStream(file);
             DBFReader reader = new DBFReader(in, cs)) {

            // Mapeo de índices
            int idxENT = -1, idxUNI = -1, idxCODO = -1, idxNOM = -1, idxOBS = -1, idxFEU = -1, idxUSR = -1, idxAPI = -1;
            int n = reader.getFieldCount();
            
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "ENTIDAD" -> idxENT = i;
                    case "UNIDAD" -> idxUNI = i;
                    case "CODOFIC" -> idxCODO = i;
                    case "NOMOFIC" -> idxNOM = i;
                    case "OBSERV" -> idxOBS = i;
                    case "FEULT" -> idxFEU = i;
                    case "USUAR" -> idxUSR = i;
                    case "API_ESTADO" -> idxAPI = i;
                }
            }

            final String ql = (q == null ? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            int rowNum = 0;

            // ⚠️ CAMBIO CRÍTICO: Bucle infinito controlado manualmente para atrapar el error de nextRecord
            while (true) {
                try {
                    // Intentamos leer el siguiente registro
                    row = reader.nextRecord();
                    
                    // Si es null, llegamos al final del archivo correctamente
                    if (row == null) break; 
                    
                    rowNum++;

                    // --- PROCESAMIENTO DEL REGISTRO (Tu lógica original) ---
                    
                    // Validar integridad mínima
                    if (idxENT == -1 || idxUNI == -1 || idxCODO == -1) continue;

                    String entidad = asString(row, idxENT);
                    String unidad = asString(row, idxUNI);
                    Integer codi = asInt(row, idxCODO);

                    if (entidad == null || entidad.isBlank() || unidad == null || unidad.isBlank() || codi == null) {
                        continue;
                    }

                    String nom = asString(row, idxNOM);
                    String observ = null;
                    try {
                        observ = asString(row, idxOBS);
                        if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) observ = null;
                    } catch (Exception e) { observ = null; }

                    String usuario = asString(row, idxUSR);

                    if (ql != null) {
                        String hay = ((entidad) + " " + (unidad) + " " + (nom != null ? nom : "")).toLowerCase(Locale.ROOT);
                        if (!hay.contains(ql)) continue;
                    }

                    LocalDate feul = null;
                    if (idxFEU >= 0 && row[idxFEU] != null) {
                        try {
                            if (row[idxFEU] instanceof java.util.Date dd) {
                                feul = new java.sql.Date(dd.getTime()).toLocalDate();
                            }
                        } catch (Exception e) { }
                    }

                    Short api = asInt(row, idxAPI) != null ? asInt(row, idxAPI).shortValue() : null;

                    out.add(OficinaDbf.builder()
                            .entidadCodigo(entidad.trim())
                            .unidad(unidad.trim())
                            .codOfi(codi.shortValue())
                            .nomOfic(nom != null ? nom.trim() : null)
                            .observ(observ)
                            .feult(feul)
                            .usuario(usuario != null ? usuario.trim() : null)
                            .apiEstado(api)
                            .build());

                } catch (Exception ex) {
                    // 🚨 AQUÍ CAPTURAMOS EL ERROR FATAL DEL REGISTRO 850
                    log.error("🔥 Error FATAL leyendo OFICINA.DBF en registro #{}. Se detiene la lectura, pero se conservan {} registros válidos.", rowNum, out.size());
                    log.debug("Detalle del error DBF: {}", ex.getMessage());
                    
                    // ROMPEMOS EL BUCLE (break) para devolver lo que ya leímos
                    break; 
                }
            }
        } catch (Exception e) {
            log.error("Error al abrir el flujo del archivo DBF", e);
            // Si falla al abrir, devolvemos lista vacía, pero no lanzamos excepción para no romper la web
        }
        
        // Devolvemos la lista parcial (ej. 849 registros)
        return out; 
    }

    /* === Convierte entidad JPA -> DTO plano para DBF === */
    public OficinaDbf mapDesdeEntidad(Oficina o) {
        String entidadCodigo = Optional.ofNullable(o.getPredio())
                .map(Predio::getEntidad)
                .map(Entidad::getEntidadCodigo)
                .orElse(null);

        String unidad = Optional.ofNullable(o.getPredio())
                .map(Predio::getUnidad)
                .orElse(null);

        return OficinaDbf.builder()
                .entidadCodigo(entidadCodigo)
                .unidad(unidad)
                .codOfi(o.getCodOfi())
                .nomOfic(o.getNombre())
                .observ(o.getObserv())
                .feult(o.getFechaUlt())
                .usuario(o.getUsuario() != null ? o.getUsuario() : "")
                .apiEstado(o.getApiEstado())
                .build();
    }

    /* === API pública para upsert desde una entidad JPA === */
    public void upsertOficinaDesdeEntidad(Oficina o) throws Exception {
        OficinaDbf dto = mapDesdeEntidad(o);
        validarClave(dto);
        upsertOficina(dto);
    }

    private void validarClave(OficinaDbf r) {
        if (r.getEntidadCodigo() == null || r.getEntidadCodigo().isBlank()
                || r.getUnidad() == null || r.getUnidad().isBlank()
                || r.getCodOfi() == null) {
            throw new IllegalArgumentException("Clave incompleta para OFICINA.DBF (ENTIDAD/UNIDAD/CODOFIC).");
        }
    }

    /* === Esquema esperado (por si toca reescribir) === */
    private DBFField[] schemaOficina() {
        DBFField f1 = new DBFField();
        f1.setName("ENTIDAD");
        f1.setType(DBFDataType.CHARACTER);
        f1.setLength(4);
        DBFField f2 = new DBFField();
        f2.setName("UNIDAD");
        f2.setType(DBFDataType.CHARACTER);
        f2.setLength(5);
        DBFField f3 = new DBFField();
        f3.setName("CODOFIC");
        f3.setType(DBFDataType.NUMERIC);
        f3.setLength(5);
        f3.setDecimalCount(0);
        DBFField f4 = new DBFField();
        f4.setName("NOMOFIC");
        f4.setType(DBFDataType.CHARACTER);
        f4.setLength(65);
        DBFField f5 = new DBFField();
        f5.setName("OBSERV");
        f5.setType(DBFDataType.MEMO); // requiere DBT
        DBFField f6 = new DBFField();
        f6.setName("FEULT");
        f6.setType(DBFDataType.DATE);
        DBFField f7 = new DBFField();
        f7.setName("USUAR");
        f7.setType(DBFDataType.CHARACTER);
        f7.setLength(8);
        DBFField f8 = new DBFField();
        f8.setName("API_ESTADO");
        f8.setType(DBFDataType.NUMERIC);
        f8.setLength(5);
        f8.setDecimalCount(0);
        return new DBFField[] { f1, f2, f3, f4, f5, f6, f7, f8 };
    }

    /* === UPSERT seguro: reescribe archivo completo === */
    public void upsertOficina(OficinaDbf nuevo) throws Exception {
        Path file = baseDir.resolve("OFICINA.DBF");
        Path tmp = baseDir.resolve("OFICINA.TMP.DBF");

        synchronized (oficinaLock) {
            // 1) cargar todos
            List<OficinaDbf> todos = listarOficinaAll(null);

            // 2) normalizar ancho de campos texto
            String ent = padRight(cut(nuevo.getEntidadCodigo(), 4), 4);
            String uni = padRight(cut(nuevo.getUnidad(), 5), 5);
            Short cod = nuevo.getCodOfi();

            // 3) buscar si existe la clave
            boolean replaced = false;
            for (int i = 0; i < todos.size(); i++) {
                var r = todos.get(i);
                if (equalsKey(r, ent, uni, cod)) {
                    todos.set(i, sanitize(nuevo));
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                todos.add(sanitize(nuevo));
            }

            // 4) escribir a TMP con esquema original
            try (OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                    DBFWriter writer = new DBFWriter(out,
                            charset != null && !charset.isBlank() ? Charset.forName(charset) : null)) {

                writer.setFields(schemaOficina());
                for (var r : todos)
                    writer.addRecord(asRow(r));
            }

            // 5) reemplazar atomáticamente
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private boolean equalsKey(OficinaDbf r, String entidad4, String unidad5, Short cod) {
        return safeEq(padRight(cut(r.getEntidadCodigo(), 4), 4), entidad4)
                && safeEq(padRight(cut(r.getUnidad(), 5), 5), unidad5)
                && Objects.equals(r.getCodOfi(), cod);
    }

    private OficinaDbf sanitize(OficinaDbf r) {
        // recortar a longitudes del DBF
        r.setEntidadCodigo(cut(r.getEntidadCodigo(), 4));
        r.setUnidad(cut(r.getUnidad(), 5));
        r.setNomOfic(cut(r.getNomOfic(), 65));
        r.setUsuario(cut(r.getUsuario() == null ? "" : r.getUsuario(), 8));
        return r;
    }

    private Object[] asRow(OficinaDbf r) {
        Date fecha = (r.getFeult() != null) ? java.sql.Date.valueOf(r.getFeult()) : null;
        Integer api = (r.getApiEstado() != null) ? r.getApiEstado().intValue() : null;

        return new Object[] {
                r.getEntidadCodigo(),
                r.getUnidad(),
                r.getCodOfi(),
                r.getNomOfic(),
                r.getObserv(), // MEMO (requiere .DBT junto al .DBF)
                fecha,
                r.getUsuario(),
                api
        };
    }

    private static boolean safeEq(String a, String b) {
        return Objects.equals(a, b);
    }

    private static String cut(String s, int n) {
        if (s == null)
            return null;
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static String padRight(String s, int n) {
        if (s == null)
            return null;
        return String.format("%1$-" + n + "s", s);
    }

    // Lector completo con filtro q
    public List<AuxiliarDbf> listarAuxiliarAll(String q) throws Exception {
        Path file = baseDir.resolve("AUXILIAR.DBF");
        List<AuxiliarDbf> out = new ArrayList<>();

        // ✅ Preparar charset ANTES de crear el reader (evita deprecation)
        Charset cs = (charset != null && !charset.isBlank())
                ? Charset.forName(charset)
                : StandardCharsets.UTF_8;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in, cs)) { // ✅ Charset en constructor

            // Mapeo de índices
            int idxENT = -1, idxUNI = -1, idxCODCONT = -1, idxCODAUX = -1,
                    idxNOMAUX = -1, idxOBS = -1, idxFEULT = -1, idxUSUAR = -1;

            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "ENTIDAD" -> idxENT = i;
                    case "UNIDAD" -> idxUNI = i;
                    case "CODCONT" -> idxCODCONT = i;
                    case "CODAUX" -> idxCODAUX = i;
                    case "NOMAUX" -> idxNOMAUX = i;
                    case "OBSERV" -> idxOBS = i;
                    case "FEULT" -> idxFEULT = i;
                    case "USUAR" -> idxUSUAR = i;
                }
            }

            final String ql = (q != null) ? q.toLowerCase(Locale.ROOT) : null;

            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                String entidad = asString(row, idxENT);
                String unidad = asString(row, idxUNI);
                Integer codcont = asInt(row, idxCODCONT);
                Integer codaux = asInt(row, idxCODAUX);
                String nomaux = asString(row, idxNOMAUX);
                String observ = asString(row, idxOBS);
                String usuar = asString(row, idxUSUAR);

                LocalDate feult = null;
                if (idxFEULT >= 0 && row[idxFEULT] != null) {
                    if (row[idxFEULT] instanceof java.util.Date dd) {
                        java.sql.Date d = new java.sql.Date(dd.getTime());
                        feult = d.toLocalDate();
                    }
                }

                // ✅ Filtro de búsqueda
                if (ql != null) {
                    String hay = String.join(" ",
                            entidad != null ? entidad : "",
                            unidad != null ? unidad : "",
                            codcont != null ? String.valueOf(codcont) : "",
                            codaux != null ? String.valueOf(codaux) : "",
                            nomaux != null ? nomaux : "",
                            usuar != null ? usuar : "").toLowerCase(Locale.ROOT);

                    if (!hay.contains(ql))
                        continue;
                }

                // ✅ Validar claves obligatorias
                if (isBlank(entidad) || isBlank(unidad) || codcont == null || codaux == null) {
                    continue;
                }

                // ✅ Normalizar observaciones MEMO
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) {
                    observ = null;
                }

                out.add(AuxiliarDbf.builder()
                        .entidadCodigo(entidad.trim())
                        .unidad(unidad.trim())
                        .codCont(codcont.shortValue())
                        .codAux(codaux.shortValue())
                        .nomAux(nomaux != null ? nomaux.trim() : null)
                        .observ(observ)
                        .fechaUlt(feult)
                        .usuario(usuar != null ? usuar.trim() : null)
                        .build());
            }
        }

        return out;
    }

    // === LECTURA DBF RESP.BDF WINDOWS
    public List<ResponsableDbf> listarResponsableAll(String q) throws Exception {
        // Cambia si tu archivo se llama distinto:
        Path file = baseDir.resolve("RESP.DBF");
        List<ResponsableDbf> out = new ArrayList<>();

        Charset cs = (charset != null && !charset.isBlank())
                ? Charset.forName(charset)
                : StandardCharsets.UTF_8;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in, cs)) {

            int idxENT = -1, idxUNI = -1, idxCODOFI = -1, idxCODRESP = -1,
                idxNOMBRE = -1, idxCI = -1, idxCARGO = -1, idxOBS = -1,
                idxFEULT = -1, idxUSUAR = -1, idxCODEXP = -1, idxAPI = -1;
            
            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "ENTIDAD" -> idxENT = i;
                    case "UNIDAD" -> idxUNI = i;
                    case "CODOFIC" -> idxCODOFI = i;
                    case "CODRESP" -> idxCODRESP = i;
                    case "NOMRESP" -> idxNOMBRE = i;
                    case "CARGO" -> idxCARGO = i;
                    case "OBSERV" -> idxOBS = i;
                    case "CI" -> idxCI = i;
                    case "FEULT" -> idxFEULT = i;
                    case "USUAR" -> idxUSUAR = i;
                    case "COD_EXP" -> idxCODEXP = i;
                    case "API_ESTADO" -> idxAPI = i;
                }
            }

            final String ql = (q != null) ? q.toLowerCase(Locale.ROOT) : null;

            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                String entidad = asString(row, idxENT);
                String unidad = asString(row, idxUNI);
                Integer codofi = asInt(row, idxCODOFI);
                String codresp = asString(row, idxCODRESP);
                String nombre = asString(row, idxNOMBRE);
                String ci = asString(row, idxCI);
                String cargo = asString(row, idxCARGO);
                String observ = asString(row, idxOBS);
                String usuar = asString(row, idxUSUAR);
                Integer codexp = asInt(row, idxCODEXP);
                Integer api = asInt(row, idxAPI);
                
                LocalDate feult = null;
                if (idxFEULT >= 0 && row[idxFEULT] != null) {
                    if (row[idxFEULT] instanceof java.util.Date dd) {
                        java.sql.Date d = new java.sql.Date(dd.getTime());
                        feult = d.toLocalDate();
                    }
                }
                
                // ✅ Filtro de búsqueda
                if (ql != null) {
                    String hay = String.join(" ",
                        entidad != null ? entidad : "",
                        unidad != null ? unidad : "",
                        codresp != null ? codresp : "",
                        nombre != null ? nombre : "",
                        ci != null ? ci : "",
                        cargo != null ? cargo : ""
                    ).toLowerCase(Locale.ROOT);
                    
                    if (!hay.contains(ql)) continue;
                }
                
                // ✅ Validar claves obligatorias
                if (isBlank(entidad) || isBlank(unidad) || codofi == null) {
                    continue;
                }
                
                // ✅ Normalizar observaciones MEMO
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) {
                    observ = null;
                }
                
                out.add(ResponsableDbf.builder()
                    .entidadCodigo(entidad.trim())
                    .unidad(unidad.trim())
                    .codOfi(codofi.shortValue())
                    .codResp(codresp != null ? codresp.trim() : null)
                    .nombre(nombre != null ? nombre.trim() : null)
                    .ci(ci != null ? ci.trim() : null)
                    .cargo(cargo != null ? cargo.trim() : null)
                    .observ(observ)
                    .fechaUlt(feult)
                    .usuario(usuar != null ? usuar.trim() : null)
                    .codExp(codexp != null ? codexp.shortValue() : null)
                    .apiEstado(api != null ? api.shortValue() : null)
                    .build());
            }
        }
        return out;
    }

    public List<OrganismoFinDbf> listarOrganismoFinAll(String q) throws Exception {
        // Ajusta el nombre del archivo si viene distinto (may/min):
        Path file = baseDir.resolve("organismo_fin.DBF");
        if (!Files.exists(file)) {
            file = baseDir.resolve("ORGANISMO_FIN.DBF");
        }

        List<OrganismoFinDbf> out = new ArrayList<>();

        Charset cs = (charset != null && !charset.isBlank())
                ? Charset.forName(charset)
                : StandardCharsets.UTF_8;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in, cs)) {

            int iGES = -1, iOF = -1, iDES = -1, iSIG = -1;
            for (int i = 0; i < reader.getFieldCount(); i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "GESTION" -> iGES = i;
                    case "OF" -> iOF = i;
                    case "DES" -> iDES = i;
                    case "SIGLA" -> iSIG = i;
                }
            }

            final String ql = (q == null ? null : q.toLowerCase(Locale.ROOT));

            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                Short ges = asInt(row, iGES) == null ? null : asInt(row, iGES).shortValue();
                String of = asString(row, iOF);
                String des = asString(row, iDES);
                String sig = asString(row, iSIG);

                if (ql != null) {
                    String hay = ((of == null ? "" : of) + " " + (des == null ? "" : des) + " "
                            + (sig == null ? "" : sig)).toLowerCase(Locale.ROOT);
                    if (!hay.contains(ql))
                        continue;
                }

                if (ges == null || of == null || of.isBlank() || des == null || des.isBlank())
                    continue;

                // Normalizaciones simples
                of = of.trim();
                des = des.trim();
                if (sig != null)
                    sig = sig.trim();

                out.add(OrganismoFinDbf.builder()
                        .gestion(ges)
                        .codOf(of)
                        .descripcion(des)
                        .sigla(sig)
                        .build());
            }
        }
        return out;
    }

    /* ====================================== */
    /* ============== REGISTRO ============== */
    /* ====================================== */

    /** Inserta un registro en CODCONT.DBF haciendo append. */
    public boolean insertCodcont(short codcont, String nombre, short vidautil,
            String observ, boolean depreciar, boolean actualizar,
            LocalDate feult, String usuar) throws Exception {

        Path dbfPath = baseDir.resolve("CODCONT.DBF");
        Path tmpPath = baseDir.resolve("CODCONT.DBF.tmp");

        synchronized (codcontLock) {
            Files.createDirectories(baseDir);

            if (!Files.exists(dbfPath)) {
                createEmptyCodcontDbf(dbfPath);
            }

            // 1) Leer registros existentes y fields
            List<Object[]> existing = new ArrayList<>();
            DBFField[] fields;
            Charset cs = (charset == null || charset.isBlank()) ? null : Charset.forName(charset);

            try (InputStream is = Files.newInputStream(dbfPath);
                    DBFReader reader = (cs == null) ? new DBFReader(is) : new DBFReader(is, cs)) {

                int nf = reader.getFieldCount();
                fields = new DBFField[nf];
                for (int i = 0; i < nf; i++) {
                    fields[i] = reader.getField(i);
                }

                Object[] row;
                while ((row = reader.nextRecord()) != null) {
                    existing.add(row);
                }
            }

            // 2) Preparar nuevo registro (mismo orden que campos)
            Object[] newRec = new Object[] {
                    Short.valueOf(codcont),
                    nombre,
                    Short.valueOf(vidautil),
                    observ,
                    Boolean.valueOf(depreciar),
                    Boolean.valueOf(actualizar),
                    feult != null ? Date.valueOf(feult) : null,
                    usuar
            };

            // 3) Escribir en archivo temporal usando constructor con Charset
            try (OutputStream os = Files.newOutputStream(tmpPath, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                    DBFWriter writer = (cs == null) ? new DBFWriter(os) : new DBFWriter(os, cs)) {

                writer.setFields(fields);
                for (Object[] rec : existing) {
                    writer.addRecord(rec);
                }
                writer.addRecord(newRec);
            }

            // 4) Reemplazar fichero (backup opcional)
            Path backup = baseDir.resolve("CODCONT.DBF.bak");
            if (Files.exists(dbfPath)) {
                Files.move(dbfPath, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            }
            Files.move(tmpPath, dbfPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            Files.deleteIfExists(backup);

            // 5) Verificación: buscar codcont
            boolean found = false;
            try (InputStream is2 = Files.newInputStream(dbfPath);
                    DBFReader vr = (cs == null) ? new DBFReader(is2) : new DBFReader(is2, cs)) {

                Object[] r;
                while ((r = vr.nextRecord()) != null) {
                    if (r.length > 0 && r[0] instanceof Number && ((Number) r[0]).shortValue() == codcont) {
                        found = true;
                        break;
                    }
                }
            }
            return found;
        }
    }

    private void createEmptyCodcontDbf(Path dbfFile) throws Exception {
        // Usar el constructor de DBFField en lugar de setters que están deprecados
        DBFField[] fields = new DBFField[8];

        fields[0] = new DBFField("CODCONT", DBFDataType.NUMERIC, 5, 0);
        fields[1] = new DBFField("NOMBRE", DBFDataType.CHARACTER, 50, 0);
        fields[2] = new DBFField("VIDAUTIL", DBFDataType.NUMERIC, 3, 0);
        fields[3] = new DBFField("OBSERV", DBFDataType.CHARACTER, 100, 0);
        fields[4] = new DBFField("DEPRECIAR", DBFDataType.LOGICAL, 1, 0);
        fields[5] = new DBFField("ACTUALIZAR", DBFDataType.LOGICAL, 1, 0);
        fields[6] = new DBFField("FEULT", DBFDataType.DATE, 8, 0);
        fields[7] = new DBFField("USUAR", DBFDataType.CHARACTER, 20, 0);

        Charset cs = (charset == null || charset.isBlank()) ? null : Charset.forName(charset);
        try (OutputStream os = Files.newOutputStream(dbfFile, StandardOpenOption.CREATE_NEW);
                DBFWriter writer = (cs == null) ? new DBFWriter(os) : new DBFWriter(os, cs)) {
            writer.setFields(fields);
            // sin registros inicialmente
        }
    }

    public int countCodcont(String filtroTexto) throws Exception {
        Path file = baseDir.resolve("CODCONT.DBF");
        int count = 0;

        Charset cs = (charset != null && !charset.isBlank())
                ? Charset.forName(charset)
                : StandardCharsets.UTF_8;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in, cs)) {

            // localizar índices que usaremos (solo NOMBRE para el filtro)
            int idxNOMBRE = -1;
            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                if ("NOMBRE".equals(name)) {
                    idxNOMBRE = i;
                }
            }

            final String q = filtroTexto == null ? null : filtroTexto.toLowerCase(Locale.ROOT);
            Object[] row;

            while ((row = reader.nextRecord()) != null) {
                if (q != null) {
                    String nom = asString(row, idxNOMBRE);
                    if (nom == null || !nom.toLowerCase(Locale.ROOT).contains(q)) {
                        continue; // no coincide con el filtro
                    }
                }
                count++;
            }
        }
        return count;
    }

    /* =========================== */
    /* ======= HELPER DBF ======== */
    /* =========================== */

    private Short asShort(Object[] row, int idx) {
        if (idx < 0 || row[idx] == null) return null;
        if (row[idx] instanceof Number n) return n.shortValue();
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private LocalDate asDate(Object[] r, int idx) {
        if (idx < 0 || r[idx] == null)
            return null;
        if (r[idx] instanceof java.util.Date d)
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        return null;
    }

    private Long asLong(Object[] r, int idx) {
        if (idx < 0 || r[idx] == null)
            return null;
        if (r[idx] instanceof Number n)
            return n.longValue();
        return Long.valueOf(r[idx].toString().trim());
    }

    private Integer asInt(Object[] r, int idx) {
        if (idx < 0 || r[idx] == null)
            return null;
        if (r[idx] instanceof Number n)
            return n.intValue();
        return Integer.valueOf(r[idx].toString().trim());
    }

    private String asString(Object[] r, int idx) {
        if (idx < 0 || r[idx] == null)
            return null;
        return r[idx].toString().trim();
    }

    private Boolean asBool(Object[] r, int idx) {
        if (idx < 0 || r[idx] == null)
            return null;
        if (r[idx] instanceof Boolean b)
            return b;
        String s = r[idx].toString().trim();
        return "T".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s) || "1".equals(s);
    }

    private static Double asDouble(Object[] row, int idx) {
        if (idx < 0)
            return null;
        Object v = row[idx];
        if (v == null)
            return null;
        if (v instanceof Double d)
            return d;
        if (v instanceof Float f)
            return (double) f;
        if (v instanceof BigDecimal bd)
            return bd.doubleValue();
        if (v instanceof Number n)
            return n.doubleValue();
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty())
                return null;
            try {
                return Double.parseDouble(t.replace(',', '.'));
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    /**
     * Lee ACTUAL.DBF — el DBF central de activos fijos.
     * ⚠️ Verifica los nombres exactos de campo contra tu archivo real.
     * Implementa el mismo patrón defensivo que listarOficinaAll (break en error).
     */
    public List<ActivoDbf> listarActualAll(String q) {
        Path file = baseDir.resolve("ACTUAL.DBF");
        List<ActivoDbf> out = new ArrayList<>();

        if (!Files.exists(file)) {
            log.warn("ACTUAL.DBF no encontrado en: {}", file);
            return out;
        }

        Charset cs = (charset != null && !charset.isBlank())
            ? Charset.forName(charset)
            : Charset.forName("CP1252");

        try (InputStream in = Files.newInputStream(file);
            DBFReader reader = new DBFReader(in, cs)) {

            // ── Mapeo de índices ────────────────────────────────────────────────
            int iUNI = -1, iENT = -1, iCOD = -1, iCODCONT = -1, iCODAUX = -1;
            int iVIDAUTIL = -1, iDESCRIP = -1, iCOSTO = -1, iDEPACU = -1;
            int iMES = -1, iANO = -1, iDIA = -1;
            int iMES_ANT = -1, iANO_ANT = -1, iDIA_ANT = -1;
            int iBREV = -1, iBANDUFV = -1, iCODESTADO = -1;
            int iCODOFIC = -1, iCODRESP = -1, iOBSERV = -1;
            int iVUT_ANT = -1, iCOSTO_ANT = -1;
            int iCOD_RUBE = -1, iNRO_CONV = -1, iORG_FIN = -1;
            int iFEULT = -1, iUSUAR = -1, iAPI = -1;
            int iCODIGOSEC = -1, iBANDERAS = -1, iFEC_MOD = -1, iUSU_MOD = -1;

            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "UNIDAD"    -> iUNI       = i;
                    case "ENTIDAD"   -> iENT       = i;
                    case "CODIGO"    -> iCOD       = i;
                    case "CODCONT"   -> iCODCONT   = i;
                    case "CODAUX"    -> iCODAUX    = i;
                    case "VIDAUTIL"  -> iVIDAUTIL  = i;
                    case "DESCRIP"   -> iDESCRIP   = i;
                    case "COSTO"     -> iCOSTO     = i;
                    case "DEPACU"    -> iDEPACU    = i;
                    case "MES"       -> iMES       = i;
                    case "ANO"       -> iANO       = i;
                    case "DIA"       -> iDIA       = i;
                    case "MES_ANT"   -> iMES_ANT   = i;
                    case "ANO_ANT"   -> iANO_ANT   = i;
                    case "DIA_ANT"   -> iDIA_ANT   = i;
                    case "VUT_ANT"   -> iVUT_ANT   = i;
                    case "COSTO_ANT" -> iCOSTO_ANT = i;
                    case "B_REV"     -> iBREV      = i;
                    case "BAND_UFV"  -> iBANDUFV   = i;
                    case "CODESTADO" -> iCODESTADO = i;
                    case "CODOFIC"   -> iCODOFIC   = i;
                    case "CODRESP"   -> iCODRESP   = i;
                    case "OBSERV"    -> iOBSERV    = i;
                    case "COD_RUBE"  -> iCOD_RUBE  = i;
                    case "NRO_CONV"  -> iNRO_CONV  = i;
                    case "ORG_FIN"   -> iORG_FIN   = i;
                    case "FEULT"     -> iFEULT     = i;
                    case "USUAR"     -> iUSUAR     = i;
                    case "API_ESTADO" -> iAPI      = i;
                    case "CODIGOSEC" -> iCODIGOSEC = i;
                    case "BANDERAS"  -> iBANDERAS  = i;
                    case "FEC_MOD"   -> iFEC_MOD   = i;
                    case "USU_MOD"   -> iUSU_MOD   = i;
                }
            }

            // Log de diagnóstico (quitar en producción)
            log.info("ACTUAL.DBF campos: ENTIDAD={} UNIDAD={} CODIGO={} CODCONT={} " +
                    "CODAUX={} CODOFIC={} CODRESP={} DIA={} MES={} ANO={} " +
                    "CODESTADO={} ORG_FIN={} COSTO={} VIDAUTIL={}",
                iENT, iUNI, iCOD, iCODCONT, iCODAUX, iCODOFIC, iCODRESP,
                iDIA, iMES, iANO, iCODESTADO, iORG_FIN, iCOSTO, iVIDAUTIL);

            final String ql = (q == null ? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            int rowNum = 0;

            while (true) {
                try {
                    row = reader.nextRecord();
                    if (row == null) break;
                    rowNum++;

                    String codigo = asString(row, iCOD);
                    if (isBlank(codigo)) continue;

                    String entidad = asString(row, iENT);
                    String unidad  = asString(row, iUNI);
                    String descrip = asString(row, iDESCRIP);

                    if (ql != null) {
                        String hay = ((codigo) + " " +
                            (entidad != null ? entidad : "") + " " +
                            (descrip != null ? descrip : "")).toLowerCase(Locale.ROOT);
                        if (!hay.contains(ql)) continue;
                    }

                    // ── Fecha adquisición desde DIA+MES+ANO ──────────────────────
                    LocalDate fechaAdq = construirFecha(
                        asInt(row, iDIA), asInt(row, iMES), asInt(row, iANO));

                    // ── Fecha anterior desde DIA_ANT+MES_ANT+ANO_ANT ─────────────
                    LocalDate fechaAnt = construirFecha(
                        asInt(row, iDIA_ANT), asInt(row, iMES_ANT), asInt(row, iANO_ANT));

                    // ── FEULT (campo DATE nativo) ──────────────────────────────────
                    LocalDate feult = null;
                    if (iFEULT >= 0 && row[iFEULT] != null) {
                        try {
                            if (row[iFEULT] instanceof java.util.Date dd)
                                feult = new java.sql.Date(dd.getTime()).toLocalDate();
                        } catch (Exception ignored) {}
                    }

                    // ── FEC_MOD (campo DATE nativo) ───────────────────────────────
                    LocalDate fecMod = null;
                    if (iFEC_MOD >= 0 && row[iFEC_MOD] != null) {
                        try {
                            if (row[iFEC_MOD] instanceof java.util.Date dd)
                                fecMod = new java.sql.Date(dd.getTime()).toLocalDate();
                        } catch (Exception ignored) {}
                    }

                    // Observaciones memo
                    String observ = null;
                    try {
                        observ = asString(row, iOBSERV);
                        if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) observ = null;
                    } catch (Exception ignored) {}

                    // ── CODRESP: en el DBF es Numeric, pero lo usamos como String ─
                    String codResp = null;
                    Integer codRespInt = asInt(row, iCODRESP);
                    if (codRespInt != null && codRespInt > 0)
                        codResp = String.valueOf(codRespInt);

                    // ── VIDAUTIL: Float en DBF → Integer ─────────────────────────
                    Integer vidaUtil = null;
                    Double vidaUtilD = asDouble(row, iVIDAUTIL);
                    if (vidaUtilD != null) vidaUtil = vidaUtilD.intValue();
                    
                    Integer codCont = asInt(row, iCODCONT);
                    Integer codAux  = asInt(row, iCODAUX);
                    Integer codOfi  = asInt(row, iCODOFIC);
                    Integer codEst  = asInt(row, iCODESTADO);
                    Integer vutAnt  = asInt(row, iVUT_ANT);

                    out.add(ActivoDbf.builder()
                        .entidadCodigo(entidad != null ? entidad.trim() : null)
                        .unidad(unidad != null ? unidad.trim() : null)
                        .codOfi(codOfi != null ? codOfi.shortValue() : null)
                        .codResp(codResp)
                        .codCont(codCont != null ? codCont.shortValue() : null)
                        .codAux(codAux != null ? codAux.shortValue() : null)
                        .codigo(codigo.trim())
                        .codigoSec(asString(row, iCODIGOSEC))
                        .descrip(descrip)
                        .costo(asDouble(row, iCOSTO))
                        .depAcu(asDouble(row, iDEPACU))
                        .costoAnt(asDouble(row, iCOSTO_ANT))
                        .vidaUtil(vidaUtil)
                        .vutAnt(vutAnt)
                        .fechaAdq(fechaAdq)
                        .fechaAnt(fechaAnt)
                        .bRev(asBool(row, iBREV))
                        .bandUfv(asBool(row, iBANDUFV))
                        .codEstado(codEst != null ? codEst.shortValue() : null)
                        .codRube(asString(row, iCOD_RUBE))
                        .nroConv(asString(row, iNRO_CONV))
                        .codOf(asString(row, iORG_FIN))
                        .banderas(asString(row, iBANDERAS))
                        .fechaUlt(feult)
                        .usuario(asString(row, iUSUAR))
                        .apiEstado(asInt(row, iAPI) != null
                            ? asInt(row, iAPI).shortValue() : null)
                        .fecMod(fecMod)
                        .usuMod(asString(row, iUSU_MOD))
                        .observ(observ)
                        .build());

                } catch (Exception ex) {
                    log.error("⚠️ Error leyendo ACTUAL.DBF en registro #{}. " +
                            "Conservando {} registros válidos. Detalle: {}",
                        rowNum, out.size(), ex.getMessage());
                    break;
                }
            }

        } catch (Exception e) {
            log.error("Error abriendo ACTUAL.DBF: {}", e.getMessage());
        }

        log.info("ACTUAL.DBF — leídos {} registros", out.size());
        return out;
    }

    // Helper: construir LocalDate desde 3 SmallInt
    private LocalDate construirFecha(Integer dia, Integer mes, Integer ano) {
        if (dia == null || mes == null || ano == null) return null;
        if (ano < 1900 || ano > 2100) return null;
        if (mes < 1 || mes > 12) return null;
        if (dia < 1 || dia > 31) return null;
        try {
            return LocalDate.of(ano, mes, dia);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Lock para escrituras en ACTUAL.DBF ──────────────────────────────────────
    private final Object actualLock = new Object();

    // ── Record interno para transportar datos raw de ACTUAL.DBF ─────────────────
    private record ActualDbfRaw(DBFField[] fields,
                                List<Object[]> rows,
                                Map<String, Integer> fieldIndex) {}

    // ────────────────────────────────────────────────────────────────────────────
    //  LECTOR: sol_transferencias.dbf desde ruta CIFS externa
    // ────────────────────────────────────────────────────────────────────────────
    public List<SolTransferenciaDbf> listarSolTransferenciasAll(Path dir, String q) {
        Path file = dir.resolve("sol_transferencias.DBF");
        List<SolTransferenciaDbf> out = new ArrayList<>();

        if (!Files.exists(file)) {
            log.warn("sol_transferencias.dbf no encontrado en: {}", file);
            return out;
        }

        Charset cs = (charset != null && !charset.isBlank())
            ? Charset.forName(charset)
            : Charset.forName("CP1252");

        try (InputStream in = Files.newInputStream(file);
            DBFReader reader = new DBFReader(in, cs)) {

            int iIDT = -1, iNOMBRET = -1, iFECHAT = -1, iESTADOT = -1, iCORRT = -1;
            int iUNIDADO = -1, iCODCONTO = -1, iCODAUXO = -1, iCODIGOO = -1;
            int iESTADOO = -1, iCODOFICO = -1, iCODRESPO = -1, iCISOLO = -1;
            int iUNIDADD = -1, iCODOFICD = -1, iCIRECEP = -1, iNOMRECEP = -1;

            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "ID_T"      -> iIDT       = i;
                    case "NOMBRE_T"  -> iNOMBRET   = i;
                    case "FECHA_T"   -> iFECHAT    = i;
                    case "ESTADO_T"  -> iESTADOT   = i;
                    case "CORR_T"    -> iCORRT     = i;
                    case "UNIDAD_O"  -> iUNIDADO   = i;
                    case "CODCONT_O" -> iCODCONTO  = i;
                    case "CODAUX_O"  -> iCODAUXO   = i;
                    case "CODIGO_O"  -> iCODIGOO   = i;
                    case "ESTADO_O"  -> iESTADOO   = i;
                    case "CODOFIC_O" -> iCODOFICO  = i;
                    case "CODRESP_O" -> iCODRESPO  = i;
                    case "CI_SOL_O"  -> iCISOLO    = i;
                    case "UNIDAD_D"  -> iUNIDADD   = i;
                    case "CODOFIC_D" -> iCODOFICD  = i;
                    case "CI_RECEP"  -> iCIRECEP   = i;
                    case "NOM_RECEP" -> iNOMRECEP  = i;
                    // _NULLFLAGS se ignora deliberadamente
                }
            }

            final String ql = (q == null ? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            int rowNum = 0;

            while (true) {
                try {
                    row = reader.nextRecord();
                    if (row == null) break;
                    rowNum++;

                    String codigoO = asString(row, iCODIGOO);
                    if (isBlank(codigoO)) continue;

                    if (ql != null) {
                        String hay = String.join(" ",
                            codigoO,
                            asString(row, iUNIDADO) != null ? asString(row, iUNIDADO) : "",
                            asString(row, iUNIDADD)  != null ? asString(row, iUNIDADD)  : "",
                            asString(row, iNOMBRET)  != null ? asString(row, iNOMBRET)  : "",
                            asString(row, iESTADOT)  != null ? asString(row, iESTADOT)  : ""
                        ).toLowerCase(Locale.ROOT);
                        if (!hay.contains(ql)) continue;
                    }

                    LocalDate fechaT = null;
                    if (iFECHAT >= 0 && row[iFECHAT] != null) {
                        try {
                            if (row[iFECHAT] instanceof java.util.Date dd)
                                fechaT = new java.sql.Date(dd.getTime()).toLocalDate();
                        } catch (Exception ignored) {}
                    }

                    out.add(SolTransferenciaDbf.builder()
                        .idT(asLong(row, iIDT))
                        .nombreT(asString(row, iNOMBRET))
                        .fechaT(fechaT)
                        .estadoT(asString(row, iESTADOT))
                        .corrT(asString(row, iCORRT))
                        .unidadO(asString(row, iUNIDADO) != null ? asString(row, iUNIDADO).trim() : null)
                        .codContO(asInt(row, iCODCONTO) != null ? asInt(row, iCODCONTO).shortValue() : null)
                        .codAuxO(asInt(row, iCODAUXO)  != null ? asInt(row, iCODAUXO).shortValue()  : null)
                        .codigoO(codigoO.trim())
                        .estadoO(asInt(row, iESTADOO)  != null ? asInt(row, iESTADOO).shortValue()  : null)
                        .codOficO(asInt(row, iCODOFICO) != null ? asInt(row, iCODOFICO).shortValue() : null)
                        .codRespO(asInt(row, iCODRESPO) != null ? asInt(row, iCODRESPO).shortValue() : null)
                        .ciSolO(asString(row, iCISOLO))
                        .unidadD(asString(row, iUNIDADD)  != null ? asString(row, iUNIDADD).trim()  : null)
                        .codOficD(asInt(row, iCODOFICD) != null ? asInt(row, iCODOFICD).shortValue() : null)
                        .ciRecep(asString(row, iCIRECEP))
                        .nomRecep(asString(row, iNOMRECEP))
                        .build());

                } catch (Exception ex) {
                    // Protección ante corrupción parcial del archivo CIFS
                    log.error("⚠️ Error leyendo sol_transferencias.dbf registro #{} — " +
                            "conservando {} registros válidos. Detalle: {}",
                            rowNum, out.size(), ex.getMessage());
                    break;
                }
            }

        } catch (IOException e) {
            // Caída de red o montaje CIFS no disponible — no interrumpe el hilo principal
            log.error("🌐 Error de red/CIFS al leer sol_transferencias.dbf: {}. Ruta: {}",
                    e.getMessage(), file);
        } catch (Exception e) {
            log.error("❌ Error inesperado leyendo sol_transferencias.dbf: {}", e.getMessage(), e);
        }

        log.info("sol_transferencias.dbf — {} registros leídos", out.size());
        return out;
    }

    // ────────────────────────────────────────────────────────────────────────────
    //  ESCRITOR: Actualiza unidad/oficina/resp de un activo en ACTUAL.DBF
    // ────────────────────────────────────────────────────────────────────────────
    public void actualizarActivoParaTransferencia(
        String    codigoActivo,
        String    entidadCodigo,
        String    unidadOrigen,
        String    unidadDestino,
        Short     codOficDestino,
        Short     codRespDestino,
        LocalDate fechaUlt,
        String    usuario) throws Exception {

        Path file = baseDir.resolve("ACTUAL.DBF");
        if (!Files.exists(file)) {
            throw new IllegalStateException("ACTUAL.DBF no encontrado en: " + file);
        }

        Charset cs = (charset != null && !charset.isBlank())
            ? Charset.forName(charset) : Charset.forName("CP1252");

        synchronized (actualLock) {
            // Reintentos ante lock del proceso legacy
            int maxIntentos = 3;
            for (int intento = 1; intento <= maxIntentos; intento++) {
                try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw");
                    FileChannel canal = raf.getChannel()) {

                    FileLock lock = canal.tryLock();
                    if (lock == null) {
                        if (intento == maxIntentos) throw new IllegalStateException(
                            "ACTUAL.DBF bloqueado por otro proceso tras " + maxIntentos + " intentos");
                        log.warn("ACTUAL.DBF bloqueado, reintento {}/{}", intento, maxIntentos);
                        Thread.sleep(500L * intento);
                        continue;
                    }

                    try {
                        DbfMeta meta = parsearCabecera(raf);
                        ejecutarUpdate(raf, meta, cs,
                            codigoActivo, entidadCodigo, unidadOrigen,
                            unidadDestino, codOficDestino, codRespDestino,
                            fechaUlt, usuario);
                        return; // éxito — salir del loop de reintentos
                    } finally {
                        lock.release();
                    }

                } catch (OverlappingFileLockException e) {
                    if (intento == maxIntentos) throw new IllegalStateException(
                        "Lock solapado en ACTUAL.DBF", e);
                    Thread.sleep(500L * intento);
                }
            }
        }
    }

    private void ejecutarUpdate(
        RandomAccessFile raf,
        DbfMeta          meta,
        Charset          cs,
        String codigoActivo, String entidadCodigo, String unidadOrigen,
        String unidadDestino, Short codOficD, Short codRespD,
        LocalDate fechaUlt, String usuario) throws IOException {

        // Resolver índices de campo UNA sola vez
        DbfFieldMeta fCodigo  = meta.field("CODIGO");
        DbfFieldMeta fEntidad = meta.field("ENTIDAD");
        DbfFieldMeta fUnidad  = meta.field("UNIDAD");
        DbfFieldMeta fCodOfi  = meta.field("CODOFIC");
        DbfFieldMeta fCodResp = meta.field("CODRESP");
        DbfFieldMeta fFeult   = meta.field("FEULT");
        DbfFieldMeta fUsuar   = meta.field("USUAR");

        if (fCodigo == null) throw new IllegalStateException(
            "Campo CODIGO no encontrado en ACTUAL.DBF");

        boolean encontrado = false;

        for (int i = 0; i < meta.recordCount(); i++) {
            long posReg = (long) meta.headerSize() + (long) i * meta.recordSize();
            raf.seek(posReg);

            byte[] reg = new byte[meta.recordSize()];
            raf.readFully(reg);

            // Saltar registros marcados como eliminados
            if (reg[0] == 0x2A) continue;   // '*' = eliminado

            // Comparar campos clave (lectura rápida desde bytes ya en memoria)
            if (!codigoActivo.equalsIgnoreCase(leerCampo(reg, fCodigo, cs)))  continue;
            if (fEntidad != null &&
                !entidadCodigo.equalsIgnoreCase(leerCampo(reg, fEntidad, cs))) continue;
            if (fUnidad != null &&
                !unidadOrigen.equalsIgnoreCase(leerCampo(reg, fUnidad, cs)))   continue;

            // ── Registro encontrado: escribir SOLO los campos que cambian ──────
            if (fUnidad  != null && unidadDestino != null)
                escribirCharacter(raf, posReg, fUnidad,  unidadDestino, cs);
            if (fCodOfi  != null && codOficD != null)
                escribirNumerico(raf, posReg, fCodOfi,  codOficD.longValue());
            if (fCodResp != null && codRespD != null)
                escribirNumerico(raf, posReg, fCodResp, codRespD.longValue());
            if (fFeult   != null && fechaUlt != null)
                escribirFecha(raf, posReg, fFeult, fechaUlt);
            if (fUsuar   != null && usuario != null)
                escribirCharacter(raf, posReg, fUsuar, usuario, cs);

            encontrado = true;
            log.info("✅ ACTUAL.DBF — activo {} actualizado en registro #{} (in-place)",
                    codigoActivo, i);
            break; // CODIGO es único — no seguir escaneando
        }

        if (!encontrado) throw new IllegalArgumentException(
            "Activo no encontrado en ACTUAL.DBF — CODIGO=" + codigoActivo
            + " ENTIDAD=" + entidadCodigo + " UNIDAD=" + unidadOrigen);
    }



    // ────────────────────────────────────────────────────────────────────────────
    //  ESCRITOR: Marca una transferencia como APROBADO en sol_transferencias.dbf
    // ────────────────────────────────────────────────────────────────────────────
    public void actualizarEstadoTransferenciaDbf(Path dir, String corrT, String nuevoEstado)
            throws Exception {

        Path file = dir.resolve("sol_transferencias.DBF");
        Path tmp  = dir.resolve("sol_transferencias.TMP.dbf");

        Charset cs = (charset != null && !charset.isBlank())
            ? Charset.forName(charset) : Charset.forName("CP1252");

        // Leer raw
        DBFField[] fields;
        List<Object[]> rows = new ArrayList<>();
        int iCORRT = -1, iESTADOT = -1;

        try (InputStream in = Files.newInputStream(file);
            DBFReader reader = new DBFReader(in, cs)) {

            int n = reader.getFieldCount();
            fields = new DBFField[n];
            for (int i = 0; i < n; i++) {
                fields[i] = reader.getField(i);
                String name = fields[i].getName().toUpperCase(Locale.ROOT);
                if ("CORR_T".equals(name))   iCORRT   = i;
                if ("ESTADO_T".equals(name)) iESTADOT = i;
            }
            Object[] row;
            while ((row = reader.nextRecord()) != null) rows.add(row);
        }

        final int fCORRT = iCORRT, fESTADOT = iESTADOT;
        rows.stream()
            .filter(r -> fCORRT >= 0 && r[fCORRT] != null &&
                        corrT.equalsIgnoreCase(r[fCORRT].toString().trim()))
            .findFirst()
            .ifPresent(r -> { if (fESTADOT >= 0) r[fESTADOT] = nuevoEstado; });

        try (OutputStream out = Files.newOutputStream(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            DBFWriter writer = new DBFWriter(out, cs)) {
            writer.setFields(fields);
            for (Object[] row : rows) writer.addRecord(row);
        }

        Files.move(tmp, file,
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE);

        log.info("✅ sol_transferencias.dbf — CORR_T={} marcado como {}", corrT, nuevoEstado);
    }

    // ── Helper privado: lee ACTUAL.DBF en modo raw (Object[]) ───────────────────
    private ActualDbfRaw leerActualRaw() throws IOException {
        Path file = baseDir.resolve("ACTUAL.DBF");
        Charset cs = (charset != null && !charset.isBlank())
            ? Charset.forName(charset) : Charset.forName("CP1252");

        try (InputStream in = Files.newInputStream(file);
            DBFReader reader = new DBFReader(in, cs)) {

            int n = reader.getFieldCount();
            DBFField[] fields = new DBFField[n];
            Map<String, Integer> idx = new HashMap<>(n);

            for (int i = 0; i < n; i++) {
                fields[i] = reader.getField(i);
                idx.put(fields[i].getName().toUpperCase(Locale.ROOT), i);
            }

            List<Object[]> rows = new ArrayList<>();
            int rowNum = 0;
            while (true) {
                try {
                    Object[] row = reader.nextRecord();
                    if (row == null) break;
                    rows.add(row);
                    rowNum++;
                } catch (Exception e) {
                    log.error("Error leyendo ACTUAL.DBF raw en fila #{}: {}", rowNum, e.getMessage());
                    break;
                }
            }
            return new ActualDbfRaw(fields, rows, idx);
        }
    }

    /**
     * Lee la cabecera y descriptores de campo del DBF en formato dBASE III/IV.
     * Ignora campos MEMO para el cálculo de offsets correctamente.
     */
    private DbfMeta parsearCabecera(RandomAccessFile raf) throws IOException {
        raf.seek(0);

        raf.skipBytes(4);                        // version + fecha última edición
        int numRegistros = leerInt32LE(raf);     // bytes 4-7
        int tamCabecera  = leerInt16LE(raf);     // bytes 8-9
        int tamRegistro  = leerInt16LE(raf);     // bytes 10-11
        raf.skipBytes(20);                       // bytes 12-31 reservados

        List<DbfFieldMeta> campos = new ArrayList<>();
        int offsetEnReg = 1; // byte 0 del registro = flag de borrado

        while (true) {
            int primerByte = raf.read();
            if (primerByte == -1 || primerByte == 0x0D) break; // terminador de cabecera

            byte[] desc = new byte[31];
            raf.readFully(desc);

            // Nombre del campo: primer byte + bytes 0-9 del descriptor
            byte[] nombreBytes = new byte[11];
            nombreBytes[0] = (byte) primerByte;
            System.arraycopy(desc, 0, nombreBytes, 1, 10);
            String nombre = new String(nombreBytes, StandardCharsets.US_ASCII)
                .replace("\0", "").trim();

            char tipo   = (char)(desc[10] & 0xFF);   // byte 11 del descriptor
            int  longit = desc[15] & 0xFF;            // byte 16 del descriptor

            campos.add(new DbfFieldMeta(nombre, tipo, offsetEnReg, longit));
            offsetEnReg += longit; // MEMO también suma (guarda puntero de 10 bytes)
        }

        return new DbfMeta(numRegistros, tamCabecera, tamRegistro, campos);
    }

    /** Lee un campo CHARACTER desde los bytes del registro ya en memoria. */
    private String leerCampo(byte[] reg, DbfFieldMeta campo, Charset cs) {
        if (campo == null || campo.recOffset() >= reg.length) return "";
        int len = Math.min(campo.length(), reg.length - campo.recOffset());
        return new String(reg, campo.recOffset(), len, cs).trim();
    }

    /** Escribe un campo CHARACTER en su posición exacta del archivo. */
    private void escribirCharacter(RandomAccessFile raf, long posReg,
            DbfFieldMeta campo, String valor, Charset cs) throws IOException {
        byte[] valorBytes = valor.getBytes(cs);
        byte[] buffer     = new byte[campo.length()];
        Arrays.fill(buffer, (byte) ' ');     // padding con espacios
        System.arraycopy(valorBytes, 0, buffer, 0,
            Math.min(valorBytes.length, buffer.length));
        raf.seek(posReg + campo.recOffset());
        raf.write(buffer);
    }

    /** Escribe un campo NUMERIC como ASCII alineado a la derecha. */
    private void escribirNumerico(RandomAccessFile raf, long posReg,
            DbfFieldMeta campo, long valor) throws IOException {
        String formateado = String.format("%" + campo.length() + "d", valor);
        if (formateado.length() > campo.length())
            formateado = formateado.substring(formateado.length() - campo.length());
        raf.seek(posReg + campo.recOffset());
        raf.write(formateado.getBytes(StandardCharsets.US_ASCII));
    }

    /** Escribe un campo DATE en formato YYYYMMDD. */
    private void escribirFecha(RandomAccessFile raf, long posReg,
            DbfFieldMeta campo, LocalDate fecha) throws IOException {
        String formateado = fecha.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        raf.seek(posReg + campo.recOffset());
        raf.write(formateado.getBytes(StandardCharsets.US_ASCII));
    }

    /** Lee int32 little-endian desde RandomAccessFile. */
    private int leerInt32LE(RandomAccessFile raf) throws IOException {
        return (raf.readUnsignedByte())
            | (raf.readUnsignedByte() << 8)
            | (raf.readUnsignedByte() << 16)
            | (raf.readUnsignedByte() << 24);
    }

    /** Lee int16 little-endian desde RandomAccessFile. */
    private int leerInt16LE(RandomAccessFile raf) throws IOException {
        return (raf.readUnsignedByte())
            | (raf.readUnsignedByte() << 8);
    }

}