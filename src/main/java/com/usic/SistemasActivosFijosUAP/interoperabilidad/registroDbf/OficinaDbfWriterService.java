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
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;

/**
 * Servicio para escribir directamente en OFICINA.DBF usando JavaDBF.
 * Omite automáticamente campos MEMO (como OBSERV) que no son soportados para escritura.
 */
@Service
public class OficinaDbfWriterService {
    
    private static final Logger log = LoggerFactory.getLogger(OficinaDbfWriterService.class);
    private final Object oficinaLock = new Object();
    
    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;
    
    public OficinaDbfWriterService() {
        log.info("Inicializando OficinaDbfWriterService con acceso directo a archivos DBF");
    }
    
    private File getOficinaDbfFile() {
        File dbfFile = new File(dbfPath, "OFICINA.DBF");
        if (!dbfFile.exists()) {
            throw new RuntimeException(
                String.format("El archivo OFICINA.DBF no existe en %s", dbfPath)
            );
        }
        if (!dbfFile.canRead() || !dbfFile.canWrite()) {
            throw new RuntimeException(
                String.format("No hay permisos de lectura/escritura en %s", dbfFile.getAbsolutePath())
            );
        }
        return dbfFile;
    }
    
    private void verificarConexionDBF() {
        try {
            File dir = new File(dbfPath);
            
            if (!dir.exists()) {
                throw new RuntimeException(
                    String.format("El directorio %s NO EXISTE. Verifique el montaje CIFS.", dbfPath)
                );
            }
            
            if (!dir.canRead()) {
                throw new RuntimeException(
                    String.format("El directorio %s NO ES LEGIBLE. Verifique permisos.", dbfPath)
                );
            }
            
            File oficinaDbf = new File(dir, "OFICINA.DBF");
            if (!oficinaDbf.exists()) {
                throw new RuntimeException(
                    String.format("El archivo OFICINA.DBF no existe en %s", dbfPath)
                );
            }
            
            log.debug("Conexión DBF verificada correctamente: {}", oficinaDbf.getAbsolutePath());
            
        } catch (Exception e) {
            log.error("No se puede conectar al DBF: {}", e.getMessage());
            throw new RuntimeException(
                "El sistema de archivos DBF no está disponible. " +
                "Verifique que /mnt/dbfwin esté montado correctamente.", e
            );
        }
    }
    
    /**
     * Verifica si existe un registro con CODOFIC dado en ENTIDAD y UNIDAD
     */
    public boolean existsByCodOfic(Short codOfic, String entidad, String unidad) {
        verificarConexionDBF();
        
        synchronized (oficinaLock) {
            try (InputStream fis = new FileInputStream(getOficinaDbfFile());
                 DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {
                
                // Buscar índices de campos
                int codOficIndex = -1;
                int entidadIndex = -1;
                int unidadIndex = -1;
                
                for (int i = 0; i < reader.getFieldCount(); i++) {
                    DBFField field = reader.getField(i);
                    String fieldName = field.getName().toUpperCase();
                    
                    if ("CODOFIC".equals(fieldName)) codOficIndex = i;
                    else if ("ENTIDAD".equals(fieldName)) entidadIndex = i;
                    else if ("UNIDAD".equals(fieldName)) unidadIndex = i;
                }
                
                if (codOficIndex == -1) {
                    throw new RuntimeException("No se encontró el campo CODOFIC en OFICINA.DBF");
                }
                
                // Buscar registro coincidente
                Object[] record;
                while ((record = reader.nextRecord()) != null) {
                    boolean match = true;
                    
                    // Comparar CODOFIC
                    if (codOficIndex >= 0 && record[codOficIndex] != null) {
                        Short recCodOfic = ((Number) record[codOficIndex]).shortValue();
                        if (!recCodOfic.equals(codOfic)) {
                            match = false;
                        }
                    }
                    
                    // Comparar ENTIDAD
                    if (match && entidadIndex >= 0 && record[entidadIndex] != null) {
                        String recEntidad = record[entidadIndex].toString().trim();
                        if (!recEntidad.equals(entidad)) {
                            match = false;
                        }
                    }
                    
                    // Comparar UNIDAD
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
                log.error("Error verificando existencia de oficina CODOFIC={}: {}", 
                         codOfic, e.getMessage());
                throw new RuntimeException("Error accediendo al DBF: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Inserta un nuevo registro en OFICINA.DBF
     */
    public void insertarDesdeOficina(Oficina oficina, String entidadCode, String unidadCode, String usuario) {
        log.info("Iniciando inserción de oficina CODOFIC={} en OFICINA.DBF", oficina.getCodOfi());
        verificarConexionDBF();
        
        synchronized (oficinaLock) {
            try {
                File dbfFile = getOficinaDbfFile();
                File tempFile = new File(dbfFile.getParent(), "OFICINA_TEMP.DBF");
                
                List<Object[]> records = new ArrayList<>();
                DBFField[] originalFields;
                
                // Leer todos los registros existentes
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
                
                // Separar campos escribibles de los MEMO
                List<DBFField> writableFieldsList = new ArrayList<>();
                List<Integer> writableIndexes = new ArrayList<>();
                
                for (int i = 0; i < originalFields.length; i++) {
                    DBFField field = originalFields[i];
                    
                    // Omitir solo campos MEMO (como OBSERV)
                    if (field.getType() != DBFDataType.MEMO) {
                        writableFieldsList.add(field);
                        writableIndexes.add(i);
                    } else {
                        log.info("Omitiendo campo MEMO '{}' (no soportado para escritura)", field.getName());
                    }
                }
                
                DBFField[] writableFields = writableFieldsList.toArray(new DBFField[0]);
                
                // Crear el nuevo registro completo
                Object[] newRecord = crearRegistroDesdeOficina(oficina, entidadCode, unidadCode, usuario, originalFields);
                records.add(newRecord);
                
                // Escribir archivo temporal (solo con campos soportados)
                try (OutputStream fos = new FileOutputStream(tempFile);
                     DBFWriter writer = new DBFWriter(fos, Charset.forName("CP1252"))) {
                    
                    writer.setFields(writableFields);
                    
                    // Escribir cada registro filtrando solo los índices escribibles
                    for (Object[] record : records) {
                        Object[] writableRecord = new Object[writableIndexes.size()];
                        for (int i = 0; i < writableIndexes.size(); i++) {
                            writableRecord[i] = record[writableIndexes.get(i)];
                        }
                        writer.addRecord(writableRecord);
                    }
                }
                
                // Crear respaldo del archivo original
                File backupFile = new File(dbfFile.getParent(), "OFICINA_BACKUP.DBF");
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
                
                // Reemplazar archivo original con el temporal
                if (!dbfFile.delete()) {
                    throw new IOException("No se pudo eliminar OFICINA.DBF original");
                }
                
                if (!tempFile.renameTo(dbfFile)) {
                    if (backupFile.exists()) {
                        backupFile.renameTo(dbfFile);
                    }
                    throw new IOException("No se pudo renombrar OFICINA_TEMP.DBF a OFICINA.DBF");
                }
                
                log.info("Oficina CODOFIC={} insertada exitosamente en OFICINA.DBF (campo OBSERV omitido)", 
                        oficina.getCodOfi());
                
            } catch (Exception e) {
                log.error("Error insertando oficina en DBF: {}", e.getMessage(), e);
                throw new RuntimeException(
                    "No se pudo registrar en OFICINA.DBF: " + e.getMessage(), e
                );
            }
        }
    }
    
    /**
     * Actualiza un registro existente en OFICINA.DBF
     */
    public void actualizarDesdeOficina(Short codOficOriginal, String entidadOriginal, String unidadOriginal,
                                       Oficina oficina, String entidadCode, String unidadCode, String usuario) {
        log.info("Iniciando actualización de oficina CODOFIC={} en OFICINA.DBF", codOficOriginal);
        verificarConexionDBF();
        
        synchronized (oficinaLock) {
            try {
                File dbfFile = getOficinaDbfFile();
                File tempFile = new File(dbfFile.getParent(), "OFICINA_TEMP.DBF");
                
                List<Object[]> records = new ArrayList<>();
                DBFField[] originalFields;
                boolean encontrado = false;
                
                // Índices de campos clave
                int codOficIndex = -1;
                int entidadIndex = -1;
                int unidadIndex = -1;
                
                // Leer archivo original
                try (InputStream fis = new FileInputStream(dbfFile);
                     DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {
                    
                    originalFields = new DBFField[reader.getFieldCount()];
                    for (int i = 0; i < reader.getFieldCount(); i++) {
                        originalFields[i] = reader.getField(i);
                        String fieldName = originalFields[i].getName().toUpperCase();
                        
                        if ("CODOFIC".equals(fieldName)) codOficIndex = i;
                        else if ("ENTIDAD".equals(fieldName)) entidadIndex = i;
                        else if ("UNIDAD".equals(fieldName)) unidadIndex = i;
                    }
                    
                    // Leer todos los registros y actualizar el que coincida
                    Object[] record;
                    while ((record = reader.nextRecord()) != null) {
                        boolean match = true;
                        
                        // Verificar coincidencia
                        if (codOficIndex >= 0 && record[codOficIndex] != null) {
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
                            // Encontrado: reemplazar con datos actualizados
                            record = crearRegistroDesdeOficina(oficina, entidadCode, unidadCode, usuario, originalFields);
                            encontrado = true;
                            log.info("Registro encontrado y actualizado: CODOFIC={}", codOficOriginal);
                        }
                        
                        records.add(record);
                    }
                }
                
                if (!encontrado) {
                    throw new RuntimeException(
                        String.format("No se encontró la oficina con CODOFIC=%s en OFICINA.DBF", codOficOriginal)
                    );
                }
                
                // Filtrar campos MEMO para escritura
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
                
                // Escribir archivo temporal
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
                
                // Crear backup
                File backupFile = new File(dbfFile.getParent(), "OFICINA_BACKUP.DBF");
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
                
                // Reemplazar archivo
                if (!dbfFile.delete()) {
                    throw new IOException("No se pudo eliminar OFICINA.DBF original");
                }
                
                if (!tempFile.renameTo(dbfFile)) {
                    if (backupFile.exists()) {
                        backupFile.renameTo(dbfFile);
                    }
                    throw new IOException("No se pudo renombrar OFICINA_TEMP.DBF a OFICINA.DBF");
                }
                
                log.info("Oficina CODOFIC={} actualizada exitosamente en OFICINA.DBF", oficina.getCodOfi());
                
            } catch (Exception e) {
                log.error("Error actualizando oficina en DBF: {}", e.getMessage(), e);
                throw new RuntimeException(
                    "No se pudo actualizar en OFICINA.DBF: " + e.getMessage(), e
                );
            }
        }
    }
    
    /**
     * Crea un arreglo con todos los campos del registro
     */
    private Object[] crearRegistroDesdeOficina(Oficina ofi, String entidadCode, String unidadCode, 
                                               String usuario, DBFField[] fields) {
        Object[] record = new Object[fields.length];
        
        // Mapeo de valores
        String ENTIDAD = entidadCode;
        String UNIDAD = unidadCode;
        Short CODOFIC = ofi.getCodOfi();
        String NOMOFIC = ofi.getNombre();
        LocalDate FEULT = LocalDate.now();
        String USUAR = (usuario != null ? usuario : "SISTEMA");
        Short API_ESTADO = (ofi.getApiEstado() != null) ? ofi.getApiEstado() : Short.valueOf("1");
        
        // Llenar el arreglo según el orden de los campos del DBF
        for (int i = 0; i < fields.length; i++) {
            String fieldName = fields[i].getName().toUpperCase();
            
            switch (fieldName) {
                case "ENTIDAD" -> record[i] = ENTIDAD;
                case "UNIDAD" -> record[i] = UNIDAD;
                case "CODOFIC" -> record[i] = CODOFIC;
                case "NOMOFIC" -> record[i] = NOMOFIC;
                case "OBSERV" -> record[i] = null; // Campo MEMO - se omite
                case "FEULT" -> record[i] = java.sql.Date.valueOf(FEULT);
                case "USUAR" -> record[i] = USUAR;
                case "API_ESTADO" -> record[i] = API_ESTADO;
                default -> {
                    record[i] = null;
                    log.debug("Campo '{}' no mapeado, establecido como null", fieldName);
                }
            }
        }
        
        return record;
    }
}
