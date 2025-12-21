package com.usic.SistemasActivosFijosUAP.interoperabilidad;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.linuxense.javadbf.DBFDataType;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFWriter;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.AuxiliarDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.EntidadDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.GrupoContableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.OficinaDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.OrganismoFinDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ResponsableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.UnidadAdminDbf;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public class JavaDbfService {
    private final Path baseDir;
    private final String charset;
    private final Object codcontLock = new Object();
    private final Object oficinaLock = new Object();

    public JavaDbfService(Path baseDir, String charset) {
        this.baseDir = baseDir;
        this.charset = charset;
    }

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
    public List<OficinaDbf> listarOficinaAll(String q) throws Exception {
        Path file = baseDir.resolve("OFICINA.DBF");
        List<OficinaDbf> out = new ArrayList<>();

        Charset cs = (charset != null && !charset.isBlank())
                ? Charset.forName(charset)
                : StandardCharsets.UTF_8;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in, cs)) {

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
            while ((row = reader.nextRecord()) != null) {
                String entidad = asString(row, idxENT);
                String unidad = asString(row, idxUNI);
                Integer codi = asInt(row, idxCODO);
                String nom = asString(row, idxNOM);
                String observ = asString(row, idxOBS);
                LocalDate feul = null;
                java.sql.Date d = (idxFEU >= 0 && row[idxFEU] instanceof java.util.Date dd)
                        ? new java.sql.Date(dd.getTime())
                        : null;
                if (d != null)
                    feul = d.toLocalDate();
                String usuario = asString(row, idxUSR);
                Short api = asInt(row, idxAPI) == null ? null : asInt(row, idxAPI).shortValue();

                if (ql != null) {
                    String hay = ((entidad == null ? "" : entidad) + " " + (unidad == null ? "" : unidad) + " " +
                            (nom == null ? "" : nom) + " " + (usuario == null ? "" : usuario) + " " +
                            (observ == null ? "" : observ)).toLowerCase(Locale.ROOT);
                    if (!hay.contains(ql))
                        continue;
                }

                if (entidad == null || entidad.isBlank() || unidad == null || unidad.isBlank() || codi == null) {
                    continue;
                }

                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim()))
                    observ = null;

                out.add(OficinaDbf.builder()
                        .entidadCodigo(entidad)
                        .unidad(unidad)
                        .codOfi(codi.shortValue())
                        .nomOfic(nom)
                        .observ(observ)
                        .feult(feul)
                        .usuario(usuario)
                        .apiEstado(api)
                        .build());
            }
        }
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

}