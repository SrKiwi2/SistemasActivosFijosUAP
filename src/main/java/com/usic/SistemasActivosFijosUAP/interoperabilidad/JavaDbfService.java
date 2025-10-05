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
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.GrupoContableDbf;

public class JavaDbfService {
    private final Path baseDir;
    private final String charset; // "CP1252", "CP850", etc.
    private final Object codcontLock = new Object();

    public JavaDbfService(Path baseDir, String charset) {
        this.baseDir = baseDir;
        this.charset = charset;
    }

    /* PARA OBTENER LOS DBF DEL WINDOWS PADRE */
    public List<GrupoContableDbf> listarCodcont(int limit, String filtroTexto) throws Exception {
        Path file = baseDir.resolve("CODCONT.DBF");
        List<GrupoContableDbf> out = new ArrayList<>();

        try (InputStream in = Files.newInputStream(file);
                DBFReader reader = new DBFReader(in)) {

            if (charset != null && !charset.isBlank()) {
                reader.setCharset(Charset.forName(charset)); // ✅ sin deprecated
            }

            int idxCODCONT = -1, idxNOMBRE = -1, idxVIDAUTIL = -1, idxDEPRECIAR = -1, idxACTUALIZAR = -1;

            // ubicar columnas por nombre
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

            Object[] row;
            int count = 0;
            final String q = filtroTexto == null ? null : filtroTexto.toLowerCase(Locale.ROOT);

            while ((row = reader.nextRecord()) != null) {
                Long cod = asLong(row, idxCODCONT);
                String nom = asString(row, idxNOMBRE);
                Integer vida = asInt(row, idxVIDAUTIL);
                Boolean dep = asBool(row, idxDEPRECIAR);
                Boolean act = asBool(row, idxACTUALIZAR);

                if (q != null && nom != null && !nom.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }

                out.add(GrupoContableDbf.builder()
                        .codContable(cod)
                        .nombre(nom)
                        .vidaUtil(vida)
                        .depreciar(dep)
                        .actualizar(act)
                        .idGrupoContable(cod) // usamos CODCONT como id
                        .build());

                if (limit > 0 && ++count >= limit)
                    break;
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
}
