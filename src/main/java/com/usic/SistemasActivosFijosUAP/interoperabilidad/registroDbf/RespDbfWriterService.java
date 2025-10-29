package com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.linuxense.javadbf.DBFDataType;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFWriter;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

/**
 * Servicio para escribir directamente en RESP.DBF usando JavaDBF.
 * Omite automáticamente campos MEMO (como OBSERV) que no son soportados para
 * escritura.
 */
@Service
public class RespDbfWriterService {
    private static final Logger log = LoggerFactory.getLogger(RespDbfWriterService.class);
    private final Object respLock = new Object();

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    public RespDbfWriterService() {
        log.info("Inicializando RespDbfWriterService con acceso directo a archivos DBF");
    }

    private File getRespDbfFile() {
        File dbfFile = new File(dbfPath, "RESP.DBF");
        if (!dbfFile.exists()) {
            throw new RuntimeException(
                    String.format("El archivo RESP.DBF no existe en %s", dbfPath));
        }
        if (!dbfFile.canRead() || !dbfFile.canWrite()) {
            throw new RuntimeException(
                    String.format("No hay permisos de lectura/escritura en %s", dbfFile.getAbsolutePath()));
        }
        return dbfFile;
    }

    private void verificarConexionDBF() {
        try {
            File dir = new File(dbfPath);

            if (!dir.exists()) {
                throw new RuntimeException(
                        String.format("El directorio %s NO EXISTE. Verifique el montaje CIFS.", dbfPath));
            }

            if (!dir.canRead()) {
                throw new RuntimeException(
                        String.format("El directorio %s NO ES LEGIBLE. Verifique permisos.", dbfPath));
            }

            File respDbf = new File(dir, "RESP.DBF");
            if (!respDbf.exists()) {
                throw new RuntimeException(
                        String.format("El archivo RESP.DBF no existe en %s", dbfPath));
            }

            log.debug("Conexión DBF verificada correctamente: {}", respDbf.getAbsolutePath());

        } catch (Exception e) {
            log.error("No se puede conectar al DBF: {}", e.getMessage());
            throw new RuntimeException(
                    "El sistema de archivos DBF no está disponible. " +
                            "Verifique que /mnt/dbfwin esté montado correctamente.",
                    e);
        }
    }

    /**
     * Verifica si existe un responsable con CODRESP en la oficina dada
     */
    public boolean existsByCodResp(Integer codResp, Short codOfic, String entidad, String unidad) {
        verificarConexionDBF();

        synchronized (respLock) {
            try (InputStream fis = new FileInputStream(getRespDbfFile());
                    DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {

                int codRespIndex = -1;
                int codOficIndex = -1;
                int entidadIndex = -1;
                int unidadIndex = -1;

                for (int i = 0; i < reader.getFieldCount(); i++) {
                    DBFField field = reader.getField(i);
                    String fieldName = field.getName().toUpperCase();

                    if ("CODRESP".equals(fieldName))
                        codRespIndex = i;
                    else if ("CODOFIC".equals(fieldName))
                        codOficIndex = i;
                    else if ("ENTIDAD".equals(fieldName))
                        entidadIndex = i;
                    else if ("UNIDAD".equals(fieldName))
                        unidadIndex = i;
                }

                if (codRespIndex == -1) {
                    throw new RuntimeException("No se encontró el campo CODRESP en RESP.DBF");
                }

                Object[] record;
                while ((record = reader.nextRecord()) != null) {
                    boolean match = true;

                    if (codRespIndex >= 0 && record[codRespIndex] != null) {
                        Integer recCodResp = ((Number) record[codRespIndex]).intValue();
                        if (!recCodResp.equals(codResp)) {
                            match = false;
                        }
                    }

                    if (match && codOficIndex >= 0 && record[codOficIndex] != null) {
                        Short recCodOfic = ((Number) record[codOficIndex]).shortValue();
                        if (!recCodOfic.equals(codOfic)) {
                            match = false;
                        }
                    }

                    if (match && entidadIndex >= 0 && record[entidadIndex] != null) {
                        String recEntidad = record[entidadIndex].toString().trim();
                        if (!recEntidad.equals(entidad)) {
                            match = false;
                        }
                    }

                    if (match && unidadIndex >= 0 && record[unidadIndex] != null) {
                        String recUnidad = record[unidadIndex].toString().trim();
                        if (!recUnidad.equals(unidad)) {
                            match = false;
                        }
                    }

                    if (match) {
                        return true;
                    }
                }

                return false;

            } catch (Exception e) {
                log.error("Error verificando existencia de responsable CODRESP={}: {}",
                        codResp, e.getMessage());
                throw new RuntimeException("Error accediendo al DBF: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Inserta un nuevo responsable en RESP.DBF
     */
    public void insertarDesdeResponsable(Responsable resp, String entidadCode, String unidadCode, String usuario) {
        log.info("Iniciando inserción de responsable CODRESP={} en RESP.DBF", resp.getCodigoFuncionario());
        verificarConexionDBF();

        synchronized (respLock) {
            try {
                File dbfFile = getRespDbfFile();
                File tempFile = new File(dbfFile.getParent(), "RESP_TEMP.DBF");

                List<Object[]> records = new ArrayList<>();
                DBFField[] originalFields;

                try (InputStream fis = new FileInputStream(dbfFile);
                        DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {

                    originalFields = new DBFField[reader.getFieldCount()];
                    for (int i = 0; i < reader.getFieldCount(); i++) {
                        originalFields[i] = reader.getField(i);
                    }

                    Object[] record;
                    while ((record = reader.nextRecord()) != null) {
                        records.add(record);
                    }
                }

                List<DBFField> writableFieldsList = new ArrayList<>();
                List<Integer> writableIndexes = new ArrayList<>();

                for (int i = 0; i < originalFields.length; i++) {
                    DBFField field = originalFields[i];

                    if (field.getType() != DBFDataType.MEMO) {
                        writableFieldsList.add(field);
                        writableIndexes.add(i);
                    } else {
                        log.info("Omitiendo campo MEMO '{}' (no soportado para escritura)", field.getName());
                    }
                }

                DBFField[] writableFields = writableFieldsList.toArray(new DBFField[0]);

                Object[] newRecord = crearRegistroDesdeResponsable(resp, entidadCode, unidadCode, usuario,
                        originalFields);
                records.add(newRecord);

                try (OutputStream fos = new FileOutputStream(tempFile);
                        DBFWriter writer = new DBFWriter(fos, Charset.forName("CP1252"))) {

                    writer.setFields(writableFields);

                    for (Object[] record : records) {
                        Object[] writableRecord = new Object[writableIndexes.size()];
                        for (int i = 0; i < writableIndexes.size(); i++) {
                            writableRecord[i] = record[writableIndexes.get(i)];
                        }
                        writer.addRecord(writableRecord);
                    }
                }

                File backupFile = new File(dbfFile.getParent(), "RESP_BACKUP.DBF");
                if (backupFile.exists()) {
                    backupFile.delete();
                }

                try (InputStream in = new FileInputStream(dbfFile);
                        OutputStream out = new FileOutputStream(backupFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                if (!dbfFile.delete()) {
                    throw new IOException("No se pudo eliminar RESP.DBF original");
                }

                if (!tempFile.renameTo(dbfFile)) {
                    if (backupFile.exists()) {
                        backupFile.renameTo(dbfFile);
                    }
                    throw new IOException("No se pudo renombrar RESP_TEMP.DBF a RESP.DBF");
                }

                log.info("Responsable CODRESP={} insertado exitosamente en RESP.DBF", resp.getCodigoFuncionario());

            } catch (Exception e) {
                log.error("Error insertando responsable en DBF: {}", e.getMessage(), e);
                throw new RuntimeException(
                        "No se pudo registrar en RESP.DBF: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Actualiza un responsable existente en RESP.DBF
     */
    public void actualizarDesdeResponsable(Integer codRespOriginal, Short codOficOriginal,
            String entidadOriginal, String unidadOriginal,
            Responsable resp, String entidadCode, String unidadCode, String usuario) {
        log.info("Iniciando actualización de responsable CODRESP={} en RESP.DBF", codRespOriginal);
        verificarConexionDBF();

        synchronized (respLock) {
            try {
                File dbfFile = getRespDbfFile();
                File tempFile = new File(dbfFile.getParent(), "RESP_TEMP.DBF");

                List<Object[]> records = new ArrayList<>();
                DBFField[] originalFields;
                boolean encontrado = false;

                int codRespIndex = -1;
                int codOficIndex = -1;
                int entidadIndex = -1;
                int unidadIndex = -1;

                try (InputStream fis = new FileInputStream(dbfFile);
                        DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {

                    originalFields = new DBFField[reader.getFieldCount()];
                    for (int i = 0; i < reader.getFieldCount(); i++) {
                        originalFields[i] = reader.getField(i);
                        String fieldName = originalFields[i].getName().toUpperCase();

                        if ("CODRESP".equals(fieldName))
                            codRespIndex = i;
                        else if ("CODOFIC".equals(fieldName))
                            codOficIndex = i;
                        else if ("ENTIDAD".equals(fieldName))
                            entidadIndex = i;
                        else if ("UNIDAD".equals(fieldName))
                            unidadIndex = i;
                    }

                    Object[] record;
                    while ((record = reader.nextRecord()) != null) {
                        boolean match = true;

                        if (codRespIndex >= 0 && record[codRespIndex] != null) {
                            Integer recCodResp = ((Number) record[codRespIndex]).intValue();
                            if (!recCodResp.equals(codRespOriginal)) {
                                match = false;
                            }
                        }

                        if (match && codOficIndex >= 0 && record[codOficIndex] != null) {
                            Short recCodOfic = ((Number) record[codOficIndex]).shortValue();
                            if (!recCodOfic.equals(codOficOriginal)) {
                                match = false;
                            }
                        }

                        if (match && entidadIndex >= 0 && record[entidadIndex] != null) {
                            String recEntidad = record[entidadIndex].toString().trim();
                            if (!recEntidad.equals(entidadOriginal)) {
                                match = false;
                            }
                        }

                        if (match && unidadIndex >= 0 && record[unidadIndex] != null) {
                            String recUnidad = record[unidadIndex].toString().trim();
                            if (!recUnidad.equals(unidadOriginal)) {
                                match = false;
                            }
                        }

                        if (match) {
                            record = crearRegistroDesdeResponsable(resp, entidadCode, unidadCode, usuario,
                                    originalFields);
                            encontrado = true;
                            log.info("Registro encontrado y actualizado: CODRESP={}", codRespOriginal);
                        }

                        records.add(record);
                    }
                }

                if (!encontrado) {
                    throw new RuntimeException(
                            String.format("No se encontró el responsable con CODRESP=%s en RESP.DBF", codRespOriginal));
                }

                List<DBFField> writableFieldsList = new ArrayList<>();
                List<Integer> writableIndexes = new ArrayList<>();

                for (int i = 0; i < originalFields.length; i++) {
                    DBFField field = originalFields[i];
                    if (field.getType() != DBFDataType.MEMO) {
                        writableFieldsList.add(field);
                        writableIndexes.add(i);
                    }
                }

                DBFField[] writableFields = writableFieldsList.toArray(new DBFField[0]);

                try (OutputStream fos = new FileOutputStream(tempFile);
                        DBFWriter writer = new DBFWriter(fos, Charset.forName("CP1252"))) {

                    writer.setFields(writableFields);

                    for (Object[] record : records) {
                        Object[] writableRecord = new Object[writableIndexes.size()];
                        for (int i = 0; i < writableIndexes.size(); i++) {
                            writableRecord[i] = record[writableIndexes.get(i)];
                        }
                        writer.addRecord(writableRecord);
                    }
                }

                File backupFile = new File(dbfFile.getParent(), "RESP_BACKUP.DBF");
                if (backupFile.exists()) {
                    backupFile.delete();
                }

                try (InputStream in = new FileInputStream(dbfFile);
                        OutputStream out = new FileOutputStream(backupFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }

                if (!dbfFile.delete()) {
                    throw new IOException("No se pudo eliminar RESP.DBF original");
                }

                if (!tempFile.renameTo(dbfFile)) {
                    if (backupFile.exists()) {
                        backupFile.renameTo(dbfFile);
                    }
                    throw new IOException("No se pudo renombrar RESP_TEMP.DBF a RESP.DBF");
                }

                log.info("Responsable CODRESP={} actualizado exitosamente en RESP.DBF", resp.getCodigoFuncionario());

            } catch (Exception e) {
                log.error("Error actualizando responsable en DBF: {}", e.getMessage(), e);
                throw new RuntimeException(
                        "No se pudo actualizar en RESP.DBF: " + e.getMessage(), e);
            }
        }
    }

    private Object[] crearRegistroDesdeResponsable(Responsable resp, String entidadCode, String unidadCode,
            String usuario, DBFField[] fields) {
        Object[] record = new Object[fields.length];

        String ENTIDAD = entidadCode;
        String UNIDAD = unidadCode;

        Short CODOFIC = null;
        if (resp.getOficina() != null && resp.getOficina().getCodOfi() != null) {
            CODOFIC = resp.getOficina().getCodOfi();
            log.debug("CODOFIC mapeado: {}", CODOFIC);
        } else {
            log.warn("Oficina.codOfi es NULL para responsable {}", resp.getIdResponsable());
        }

        Integer CODRESP = null;
        if (resp.getCodigoFuncionario() != null && !resp.getCodigoFuncionario().trim().isEmpty()) {
            String onlyDigits = resp.getCodigoFuncionario().replaceAll("\\D+", "");
            if (!onlyDigits.isEmpty()) {
                try {
                    CODRESP = Integer.valueOf(onlyDigits);
                    log.debug("CODRESP mapeado: {}", CODRESP);
                } catch (Exception e) {
                    log.error("Error convirtiendo CODRESP: {}", e.getMessage());
                }
            }
        }

        String NOMBRESP = null;
        if (resp.getPersona() != null) {
            StringBuilder nombreCompleto = new StringBuilder();
            
            // Nombre (siempre debe ir)
            if (resp.getPersona().getNombre() != null) {
                nombreCompleto.append(resp.getPersona().getNombre().trim());
            }

            // Paterno
            if (resp.getPersona().getPaterno() != null && !resp.getPersona().getPaterno().trim().isEmpty()) {
                if (nombreCompleto.length() > 0) nombreCompleto.append(" ");
                nombreCompleto.append(resp.getPersona().getPaterno().trim());
            }

            // Materno (opcional)
            if (resp.getPersona().getMaterno() != null && !resp.getPersona().getMaterno().trim().isEmpty()) {
                if (nombreCompleto.length() > 0) nombreCompleto.append(" ");
                nombreCompleto.append(resp.getPersona().getMaterno().trim());
            }

            NOMBRESP = nombreCompleto.toString().toUpperCase(); // Usar mayúsculas para DBF
            log.debug("NOMBRESP mapeado: {}", NOMBRESP);
        } else {
            log.warn("Persona es NULL para responsable {}", resp.getIdResponsable());
        }

        String CARGO = null;
        if (resp.getCargo() != null && resp.getCargo().getNombre() != null) {
            CARGO = resp.getCargo().getNombre();
            log.debug("CARGO mapeado: {}", CARGO);
        }

        String CI = null;
        if (resp.getPersona() != null && resp.getPersona().getCi() != null) {
            CI = resp.getPersona().getCi();
            if (resp.getPersona().getExtension() != null) {
                CI += " " + resp.getPersona().getExtension();
            }
            log.debug("CI mapeado: {}", CI);
        }

        LocalDate FEULT = LocalDate.now();
        String USUAR = (usuario != null && !usuario.trim().isEmpty()) ? usuario : "SISTEMA";
        Short COD_EXP = (resp.getCodExp() != null) ? resp.getCodExp() : Short.valueOf("0");
        Short API_ESTADO = (resp.getApiEstado() != null) ? resp.getApiEstado() : Short.valueOf("1");

        for (int i = 0; i < fields.length; i++) {
            String fieldName = fields[i].getName().toUpperCase();

            switch (fieldName) {
                case "ENTIDAD" -> record[i] = ENTIDAD;
                case "UNIDAD" -> record[i] = UNIDAD;
                case "CODOFIC" -> record[i] = CODOFIC;
                case "CODRESP" -> record[i] = CODRESP;
                case "NOMBRESP" -> record[i] = NOMBRESP;
                case "CARGO" -> record[i] = CARGO;
                case "OBSERV" -> record[i] = null; // MEMO
                case "CI" -> record[i] = CI;
                case "FEULT" -> record[i] = java.sql.Date.valueOf(FEULT);
                case "USUAR" -> record[i] = USUAR;
                case "COD_EXP" -> record[i] = COD_EXP;
                case "API_ESTADO" -> record[i] = API_ESTADO;
                default -> {
                    record[i] = null;
                    log.debug("Campo '{}' no mapeado", fieldName);
                }
            }
        }

        log.debug("Registro DBF creado - CODOFIC: {}, CODRESP: {}, NOMBRESP: {}, CI: {}",
                CODOFIC, CODRESP, NOMBRESP, CI);

        return record;
    }
}
