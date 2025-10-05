package com.usic.SistemasActivosFijosUAP.interoperabilidad;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFWriter;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.EntidadDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.GrupoContableDbf;
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

    /** Lee TODO unidadadmin.DBF, con filtro opcional por texto (en código, unidad, descrip, ciudad). */
    public List<UnidadAdminDbf> listarUnidadAdminAll(String q) throws Exception {
        Path file = baseDir.resolve("unidadadmin.DBF");
        List<UnidadAdminDbf> out = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
            DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset));
            }

            int idxENTIDAD=-1, idxUNIDAD=-1, idxDESCRIP=-1, idxCIUDAD=-1, idxESTADO=-1;
            int n = reader.getFieldCount();
            for (int i=0;i<n;i++) {
                String name = reader.getField(i).getName().toUpperCase(Locale.ROOT);
                switch (name) {
                    case "ENTIDAD"   -> idxENTIDAD=i;
                    case "UNIDAD"    -> idxUNIDAD=i;
                    case "DESCRIP"   -> idxDESCRIP=i;
                    case "CIUDAD"    -> idxCIUDAD=i;
                    case "ESTADOUNI" -> idxESTADO=i;
                }
            }

            final String ql = (q==null? null : q.toLowerCase(Locale.ROOT));
            Object[] row;
            while ((row = reader.nextRecord()) != null) {
                String entidad = asString(row, idxENTIDAD);
                String unidad  = asString(row, idxUNIDAD);
                String descrip = asString(row, idxDESCRIP);
                String ciudad  = asString(row, idxCIUDAD);
                Short  estado  = asInt(row, idxESTADO) == null ? null : asInt(row, idxESTADO).shortValue();

                if (ql != null) {
                    String hay = ((entidad==null?"":entidad)+" "+(unidad==null?"":unidad)+" "+(descrip==null?"":descrip)+" "+(ciudad==null?"":ciudad)).toLowerCase(Locale.ROOT);
                    if (!hay.contains(ql)) continue;
                }

                if ((entidad==null || entidad.isBlank()) || (unidad==null || unidad.isBlank())) {
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