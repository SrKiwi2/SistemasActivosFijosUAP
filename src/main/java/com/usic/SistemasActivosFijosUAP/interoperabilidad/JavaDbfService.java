package com.usic.SistemasActivosFijosUAP.interoperabilidad;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFWriter;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.AuxiliarDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.EntidadDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.GrupoContableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.OficinaDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.OrganismoFinDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ResponsableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.UnidadAdminDbf;

public class JavaDbfService {
    private final Path baseDir;
    private final String charset; // "CP1252", "CP850", etc.
    private final Object codcontLock = new Object();

    public JavaDbfService(Path baseDir, String charset) {
        this.baseDir = baseDir;
        this.charset = charset;
    }

    // === LECTOR COMPLETO SIN PAGINAR ===
    public List<GrupoContableDbf> listarCodcontAll(String filtroTexto) throws Exception {
        Path file = baseDir.resolve("CODCONT.DBF");
        List<GrupoContableDbf> out = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset));
            }

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

    // === LECTOR DE ENTIDADES ===
    public List<EntidadDbf> listarEntidadesAll(Short gestionFiltro, String q) throws Exception {
        // Ajusta el nombre EXACTO si en tu share está con otra capitalización:
        Path file = baseDir.resolve("entidades.DBF");
        List<EntidadDbf> out = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset));
            }

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

    /**
     * Lee TODO unidadadmin.DBF, con filtro opcional por texto (en código, unidad,
     * descrip, ciudad).
     */
    public List<UnidadAdminDbf> listarUnidadAdminAll(String q) throws Exception {
        Path file = baseDir.resolve("unidadadmin.DBF");
        List<UnidadAdminDbf> out = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset));
            }

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
                    continue; // claves vacías: descarta
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

    /* === LECTOR DE OFICINA DESDE DF WINDOWS */
    public List<OficinaDbf> listarOficinaAll(String q) throws Exception {
        Path file = baseDir.resolve("OFICINA.DBF"); // ojo al nombre real/case
        List<OficinaDbf> out = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset));
            }

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
                String observ = asString(row, idxOBS); // si el lib entrega "(Memo)", la limpiamos abajo
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
                    continue; // claves incompletas
                }

                // limpia literal "(memo)"
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

    // Lector completo con filtro q
    public List<AuxiliarDbf> listarAuxiliarAll(String q) throws Exception {
        Path file = baseDir.resolve("AUXILIAR.DBF");
        List<AuxiliarDbf> out = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset));
            }

            int iENT = -1, iUNI = -1, iCC = -1, iCA = -1, iNOM = -1, iOBS = -1, iF = -1, iUSR = -1;
            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "ENTIDAD" -> iENT = i;
                    case "UNIDAD" -> iUNI = i;
                    case "CODCONT" -> iCC = i;
                    case "CODAUX" -> iCA = i;
                    case "NOMAUX" -> iNOM = i;
                    case "OBSERV" -> iOBS = i;
                    case "FEULT" -> iF = i;
                    case "USUAR" -> iUSR = i;
                }
            }

            final String ql = (q == null ? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                String entidad = asString(row, iENT);
                String unidad = asString(row, iUNI);
                Integer cc = asInt(row, iCC);
                Integer ca = asInt(row, iCA);
                String nom = asString(row, iNOM);
                String obs = asString(row, iOBS);
                LocalDate fult = null;
                if (iF >= 0 && row[iF] instanceof java.util.Date d)
                    fult = new java.sql.Date(d.getTime()).toLocalDate();
                String usr = asString(row, iUSR);

                if (ql != null) {
                    String hay = ((entidad == null ? "" : entidad) + " " + (unidad == null ? "" : unidad) + " " +
                            (nom == null ? "" : nom) + " " + (usr == null ? "" : usr) + " " + (obs == null ? "" : obs))
                            .toLowerCase(Locale.ROOT);
                    if (!hay.contains(ql))
                        continue;
                }

                if (entidad == null || entidad.isBlank() || unidad == null || unidad.isBlank() || cc == null
                        || ca == null) {
                    continue; // claves incompletas
                }

                if (obs != null && "(memo)".equalsIgnoreCase(obs.trim()))
                    obs = null;

                out.add(AuxiliarDbf.builder()
                        .entidadCodigo(entidad)
                        .unidad(unidad)
                        .codCont(cc.shortValue())
                        .codAux(ca.shortValue())
                        .nomAux(nom)
                        .observ(obs)
                        .fechaUlt(fult)
                        .usuario(usr)
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

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset));
            }

            int iENT = -1, iUNI = -1, iCODOF = -1, iCODR = -1, iNOM = -1, iCARGO = -1, iOBS = -1, iCI = -1, iFE = -1,
                    iUSU = -1, iCODEXP = -1, iAPI = -1;

            int n = reader.getFieldCount();
            for (int i = 0; i < n; i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "ENTIDAD" -> iENT = i;
                    case "UNIDAD" -> iUNI = i;
                    case "CODOFIC", "CODOFI", "COD_OFI" -> iCODOF = i;
                    case "CODRESP", "COD_RESP" -> iCODR = i;
                    case "NOMRESP", "NOMBRE", "RESP" -> iNOM = i;
                    case "CARGO" -> iCARGO = i;
                    case "OBSERV", "OBS" -> iOBS = i;
                    case "CI", "CEDULA" -> iCI = i;
                    case "FEULT", "FECHA", "F_ULT" -> iFE = i;
                    case "USUAR", "USUARIO" -> iUSU = i;
                    case "COD_EXP", "CODEXP" -> iCODEXP = i;
                    case "API_ESTADO", "APIESTADO" -> iAPI = i;
                }
            }

            final String ql = (q == null ? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                String ent = asString(row, iENT);
                String uni = asString(row, iUNI);
                Short codOf = asInt(row, iCODOF) == null ? null : asInt(row, iCODOF).shortValue();
                // codResp en DBF es numérico; guárdalo como texto o como short. Aquí lo guardo
                // TEXT (más flexible):
                Short codR = asInt(row, iCODR) == null ? null : asInt(row, iCODR).shortValue();
                String nom = asString(row, iNOM);
                String car = asString(row, iCARGO);
                String obs = asString(row, iOBS);
                String ci = asString(row, iCI);
                LocalDate fe = asDate(row, iFE);
                String usu = asString(row, iUSU);
                Short cexp = asInt(row, iCODEXP) == null ? null : asInt(row, iCODEXP).shortValue();
                Short api = asInt(row, iAPI) == null ? null : asInt(row, iAPI).shortValue();

                if (ql != null) {
                    String hay = (String.join(" ",
                            ent == null ? "" : ent, uni == null ? "" : uni, String.valueOf(codOf == null ? "" : codOf),
                            String.valueOf(codR == null ? "" : codR), nom == null ? "" : nom, car == null ? "" : car,
                            ci == null ? "" : ci)).toLowerCase(Locale.ROOT);
                    if (!hay.contains(ql))
                        continue;
                }

                if (isBlank(ent) || isBlank(uni) || codOf == null)
                    continue; // claves mínimas

                // Normaliza OBS "(memo)"
                if (obs != null && "(memo)".equalsIgnoreCase(obs.trim()))
                    obs = null;

                out.add(ResponsableDbf.builder()
                        .entidadCodigo(ent)
                        .unidad(uni)
                        .codOfi(codOf)
                        .codResp(codR == null ? null : String.valueOf(codR))
                        .nombre(nom)
                        .cargo(car)
                        .observ(obs)
                        .ci(ci)
                        .fechaUlt(fe)
                        .usuario(usu)
                        .codExp(cexp)
                        .apiEstado(api)
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
        try (InputStream in = Files.newInputStream(file);
            DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
            reader.setCharset(Charset.forName(charset));
            }

            int iGES=-1, iOF=-1, iDES=-1, iSIG=-1;
            for (int i=0;i<reader.getFieldCount();i++) {
            String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
            switch (name) {
                case "GESTION" -> iGES=i;
                case "OF"      -> iOF=i;
                case "DES"     -> iDES=i;
                case "SIGLA"   -> iSIG=i;
            }
            }

            final String ql = (q==null? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            while ((row = reader.nextRecord()) != null) {
            Short  ges = asInt(row, iGES)==null ? null : asInt(row, iGES).shortValue();
            String of  = asString(row, iOF);
            String des = asString(row, iDES);
            String sig = asString(row, iSIG);

            if (ql != null) {
                String hay = ((of==null?"":of)+" "+(des==null?"":des)+" "+(sig==null?"":sig)).toLowerCase(Locale.ROOT);
                if (!hay.contains(ql)) continue;
            }

            if (ges==null || of==null || of.isBlank() || des==null || des.isBlank()) continue;

            // Normalizaciones simples
            of  = of.trim();
            des = des.trim();
            if (sig!=null) sig = sig.trim();

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

    /** Inserta un registro en CODCONT.DBF haciendo append. */
    public void insertCodcont(
            short codcont, String nombre, short vidautil,
            String observ, boolean depreciar, boolean actualizar,
            LocalDate feult, String usuar) throws Exception {
        var dbfFile = baseDir.resolve("CODCONT.DBF").toFile();

        synchronized (codcontLock) {
            // Abrimos el DBF existente y escribimos al final (append)
            try (var writer = new DBFWriter(dbfFile)) {
                // Charset correcto para tildes/ñ
                if (charset != null && !charset.isBlank()) {
                    writer.setCharset(Charset.forName(charset));
                }

                // Orden de columnas: CODCONT, NOMBRE, VIDAUTIL, OBSERV, DEPRECIAR, ACTUALIZAR,
                // FEULT, USUAR
                var record = new Object[] {
                        Short.valueOf(codcont),
                        nombre,
                        Short.valueOf(vidautil),
                        observ, // puede ir null
                        Boolean.valueOf(depreciar),
                        Boolean.valueOf(actualizar),
                        feult != null ? Date.valueOf(feult) : null,
                        usuar
                };

                writer.addRecord(record);
                // writer.close() se llama automáticamente por try-with-resources
            }
        }
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

    public int countCodcont(String filtroTexto) throws Exception {
        Path file = baseDir.resolve("CODCONT.DBF");
        int count = 0;

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset));
            }

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
}