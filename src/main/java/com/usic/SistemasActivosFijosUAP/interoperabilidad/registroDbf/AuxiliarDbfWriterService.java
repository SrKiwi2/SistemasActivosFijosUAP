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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.linuxense.javadbf.DBFDataType;
import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFWriter;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.service.DbfColaService;

/**
 * Servicio para escribir directamente en auxiliar.DBF usando JavaDBF.
 * Omite automáticamente campos MEMO (como OBSERV) que no son soportados para escritura.
 */
@Service
public class AuxiliarDbfWriterService {
    private static final Logger log = LoggerFactory.getLogger(AuxiliarDbfWriterService.class);
    private final Object auxiliarLock = new Object();
    
    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    @Value("${legacy.dbf.write.mode:bytes}")
    private String writeMode;

    @Autowired
    private DbfColaService colaService;

    public AuxiliarDbfWriterService() {
        log.info("Inicializando AuxiliarDbfWriterService con acceso directo a archivos DBF");
    }
    
    private File getAuxiliarDbfFile() {
        File dbfFile = new File(dbfPath, "auxiliar.DBF");
        if (!dbfFile.exists()) {
            throw new RuntimeException(
                String.format("El archivo auxiliar.DBF no existe en %s", dbfPath)
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
            
            File auxiliarDbf = new File(dir, "auxiliar.DBF");
            if (!auxiliarDbf.exists()) {
                throw new RuntimeException(
                    String.format("El archivo auxiliar.DBF no existe en %s", dbfPath)
                );
            }
            
            log.debug("Conexión DBF verificada correctamente: {}", auxiliarDbf.getAbsolutePath());
            
        } catch (Exception e) {
            log.error("No se puede conectar al DBF: {}", e.getMessage());
            throw new RuntimeException(
                "El sistema de archivos DBF no está disponible. " +
                "Verifique que /mnt/dbfwin esté montado correctamente.", e
            );
        }
    }
    
    /**
     * Verifica si existe un registro con CODCONT y CODAUX dado
     */
    public boolean existsByCodContYCodAux(Short codCont, Short codAux, String entidad, String unidad) {
        verificarConexionDBF();
        
        synchronized (auxiliarLock) {
            try (InputStream fis = new FileInputStream(getAuxiliarDbfFile());
                 DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {
                
                int codContIndex = -1;
                int codAuxIndex = -1;
                int entidadIndex = -1;
                int unidadIndex = -1;
                
                for (int i = 0; i < reader.getFieldCount(); i++) {
                    DBFField field = reader.getField(i);
                    String fieldName = field.getName().toUpperCase();
                    
                    if ("CODCONT".equals(fieldName)) codContIndex = i;
                    else if ("CODAUX".equals(fieldName)) codAuxIndex = i;
                    else if ("ENTIDAD".equals(fieldName)) entidadIndex = i;
                    else if ("UNIDAD".equals(fieldName)) unidadIndex = i;
                }
                
                if (codContIndex == -1 || codAuxIndex == -1) {
                    throw new RuntimeException("No se encontraron los campos CODCONT o CODAUX en auxiliar.DBF");
                }
                
                Object[] record;
                while ((record = reader.nextRecord()) != null) {
                    boolean match = true;
                    
                    if (codContIndex >= 0 && record[codContIndex] != null) {
                        Integer recCodCont = ((Number) record[codContIndex]).intValue();
                        if (!recCodCont.equals(codCont.intValue())) {
                            match = false;
                        }
                    }
                    
                    if (match && codAuxIndex >= 0 && record[codAuxIndex] != null) {
                        Short recCodAux = ((Number) record[codAuxIndex]).shortValue();
                        if (!recCodAux.equals(codAux)) {
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
                log.error("Error verificando existencia de auxiliar CODCONT={}, CODAUX={}: {}", 
                         codCont, codAux, e.getMessage());
                throw new RuntimeException("Error accediendo al DBF: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Garantiza que el auxiliar exista en el VSIAF, deduciendo ENTIDAD y UNIDAD de su
     * propio predio. Es el <b>único</b> punto de entrada que deben usar los módulos que
     * no son el ABM de auxiliares (registro de activos, pendientes, transferencias):
     * así todos escriben por la misma vía —la cola del worker VFPOLEDB, que mantiene el
     * índice .CDX— en lugar de reescribir el DBF por su cuenta.
     * <p>
     * En modo cola la orden es un INSERT idempotente: el worker inserta sólo si no
     * existe la clave (ENTIDAD, UNIDAD, CODCONT, CODAUX), de modo que llamarlo por cada
     * activo aprobado no duplica nada.
     * <p>
     * No hace nada si {@code auxiliar} es null (el auxiliar es opcional en un activo).
     *
     * @throws IllegalStateException si al auxiliar le faltan predio, entidad o unidad;
     *         quien llama decide si eso aborta la operación o sólo se advierte.
     */
    public void asegurarEnVsiaf(Auxiliar auxiliar, String usuario) {
        if (auxiliar == null) return;

        if (auxiliar.getPredio() == null
                || auxiliar.getPredio().getEntidad() == null
                || auxiliar.getPredio().getEntidad().getEntidadCodigo() == null
                || auxiliar.getPredio().getEntidad().getEntidadCodigo().isBlank()
                || auxiliar.getPredio().getUnidad() == null
                || auxiliar.getPredio().getUnidad().isBlank()) {
            throw new IllegalStateException(String.format(
                "El auxiliar '%s' (id=%s) no tiene predio/entidad/unidad: no se puede ubicar en el VSIAF.",
                auxiliar.getNombre(), auxiliar.getIdAuxiliar()));
        }
        if (auxiliar.getGrupoContable() == null || auxiliar.getGrupoContable().getCodContable() == null) {
            throw new IllegalStateException(String.format(
                "El auxiliar '%s' (id=%s) no tiene grupo contable: el CODAUX no significa nada sin CODCONT.",
                auxiliar.getNombre(), auxiliar.getIdAuxiliar()));
        }
        if (auxiliar.getCodAux() == null) {
            throw new IllegalStateException(String.format(
                "El auxiliar '%s' (id=%s) no tiene CODAUX asignado.",
                auxiliar.getNombre(), auxiliar.getIdAuxiliar()));
        }

        insertarDesdeAuxiliar(
            auxiliar,
            auxiliar.getPredio().getEntidad().getEntidadCodigo(),
            auxiliar.getPredio().getUnidad(),
            usuario);
    }

    /**
     * Inserta un nuevo registro en auxiliar.DBF
     */
    public void insertarDesdeAuxiliar(Auxiliar auxiliar, String entidadCode, String unidadCode, String usuario) {
        // ── Modo COLA: dejar la orden para el worker VFPOLEDB (mantiene el índice .CDX) ──
        if ("cola".equalsIgnoreCase(writeMode)) {
            colaService.encolarInsert("AUXILIAR", construirCamposAuxiliar(auxiliar, entidadCode, unidadCode, usuario));
            log.info("📤 Auxiliar CODAUX={} encolado para VSIAF (modo cola)", auxiliar.getCodAux());
            return;
        }

        log.info("Iniciando inserción de auxiliar CODCONT={}, CODAUX={} en auxiliar.DBF",
                auxiliar.getGrupoContable().getCodContable(), auxiliar.getCodAux());
        verificarConexionDBF();
        
        synchronized (auxiliarLock) {
            try {
                File dbfFile = getAuxiliarDbfFile();
                File tempFile = new File(dbfFile.getParent(), "auxiliar_TEMP.DBF");
                
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
                
                Object[] newRecord = crearRegistroDesdeAuxiliar(auxiliar, entidadCode, unidadCode, usuario, originalFields);
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
                
                File backupFile = new File(dbfFile.getParent(), "auxiliar_BACKUP.DBF");
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
                    throw new IOException("No se pudo eliminar auxiliar.DBF original");
                }
                
                if (!tempFile.renameTo(dbfFile)) {
                    if (backupFile.exists()) {
                        backupFile.renameTo(dbfFile);
                    }
                    throw new IOException("No se pudo renombrar auxiliar_TEMP.DBF a auxiliar.DBF");
                }
                
                log.info("Auxiliar CODCONT={}, CODAUX={} insertado exitosamente en auxiliar.DBF (campo OBSERV omitido)", 
                        auxiliar.getGrupoContable().getCodContable(), auxiliar.getCodAux());
                
            } catch (Exception e) {
                log.error("Error insertando auxiliar en DBF: {}", e.getMessage(), e);
                throw new RuntimeException(
                    "No se pudo registrar en auxiliar.DBF: " + e.getMessage(), e
                );
            }
        }
    }
    
    /**
     * Actualiza un registro existente en auxiliar.DBF
     */
    public void actualizarDesdeAuxiliar(Short codContOriginal, Short codAuxOriginal,
                                        String entidadOriginal, String unidadOriginal,
                                        Auxiliar auxiliar, String entidadCode, String unidadCode, String usuario) {
        // ── Modo COLA: encolar UPDATE para el worker VFPOLEDB (mantiene el índice .CDX) ──
        if ("cola".equalsIgnoreCase(writeMode)) {
            Map<String, Object> clave = new LinkedHashMap<>();
            clave.put("ENTIDAD", entidadOriginal);
            clave.put("UNIDAD", unidadOriginal);
            clave.put("CODCONT", codContOriginal);
            clave.put("CODAUX", codAuxOriginal);
            colaService.encolarUpdate("AUXILIAR", clave, construirCamposAuxiliar(auxiliar, entidadCode, unidadCode, usuario));
            log.info("📤 Auxiliar CODAUX={} encolado para UPDATE en VSIAF (modo cola)", codAuxOriginal);
            return;
        }

        log.info("Iniciando actualización de auxiliar CODCONT={}, CODAUX={} en auxiliar.DBF",
                codContOriginal, codAuxOriginal);
        verificarConexionDBF();
        
        synchronized (auxiliarLock) {
            try {
                File dbfFile = getAuxiliarDbfFile();
                File tempFile = new File(dbfFile.getParent(), "auxiliar_TEMP.DBF");
                
                List<Object[]> records = new ArrayList<>();
                DBFField[] originalFields;
                boolean encontrado = false;
                
                int codContIndex = -1;
                int codAuxIndex = -1;
                int entidadIndex = -1;
                int unidadIndex = -1;
                
                try (InputStream fis = new FileInputStream(dbfFile);
                     DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {
                    
                    originalFields = new DBFField[reader.getFieldCount()];
                    for (int i = 0; i < reader.getFieldCount(); i++) {
                        originalFields[i] = reader.getField(i);
                        String fieldName = originalFields[i].getName().toUpperCase();
                        
                        if ("CODCONT".equals(fieldName)) codContIndex = i;
                        else if ("CODAUX".equals(fieldName)) codAuxIndex = i;
                        else if ("ENTIDAD".equals(fieldName)) entidadIndex = i;
                        else if ("UNIDAD".equals(fieldName)) unidadIndex = i;
                    }
                    
                    Object[] record;
                    while ((record = reader.nextRecord()) != null) {
                        boolean match = true;
                        
                        if (codContIndex >= 0 && record[codContIndex] != null) {
                            Integer recCodCont = ((Number) record[codContIndex]).intValue();
                            if (!recCodCont.equals(codContOriginal.intValue())) {
                                match = false;
                            }
                        }
                        
                        if (match && codAuxIndex >= 0 && record[codAuxIndex] != null) {
                            Short recCodAux = ((Number) record[codAuxIndex]).shortValue();
                            if (!recCodAux.equals(codAuxOriginal)) {
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

                            record = crearRegistroDesdeAuxiliar(auxiliar, entidadCode, unidadCode, usuario, originalFields);
                            encontrado = true;
                            log.info("Registro encontrado y actualizado: CODCONT={}, CODAUX={}", 
                                    codContOriginal, codAuxOriginal);
                        }
                        
                        records.add(record);
                    }
                }
                
                if (!encontrado) {
                    throw new RuntimeException(
                        String.format("No se encontró el auxiliar con CODCONT=%s, CODAUX=%s en auxiliar.DBF", 
                                     codContOriginal, codAuxOriginal)
                    );
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
                
                File backupFile = new File(dbfFile.getParent(), "auxiliar_BACKUP.DBF");
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
                    throw new IOException("No se pudo eliminar auxiliar.DBF original");
                }
                
                if (!tempFile.renameTo(dbfFile)) {
                    if (backupFile.exists()) {
                        backupFile.renameTo(dbfFile);
                    }
                    throw new IOException("No se pudo renombrar auxiliar_TEMP.DBF a auxiliar.DBF");
                }
                
                log.info("Auxiliar CODCONT={}, CODAUX={} actualizado exitosamente en auxiliar.DBF", 
                        auxiliar.getGrupoContable().getCodContable(), auxiliar.getCodAux());
                
            } catch (Exception e) {
                log.error("Error actualizando auxiliar en DBF: {}", e.getMessage(), e);
                throw new RuntimeException(
                    "No se pudo actualizar en auxiliar.DBF: " + e.getMessage(), e
                );
            }
        }
    }
    
    /**
     * Crea un arreglo con todos los campos del registro
     */
    /** Arma el mapa campo→valor (crudo) de AUXILIAR para encolar la orden al worker VFPOLEDB. */
    private Map<String, Object> construirCamposAuxiliar(Auxiliar aux, String ent, String uni, String usr) {
        Short codcont = null;
        if (aux.getGrupoContable() != null && aux.getGrupoContable().getCodContable() != null) {
            try {
                codcont = Short.valueOf(aux.getGrupoContable().getCodContable().toString().trim());
            } catch (Exception ignored) { }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        // Los anchos son los de auxiliar.DBF: pasarse hace que el worker rechace la orden
        // o que el VSIAF guarde el texto cortado por su cuenta (y entonces el nombre deja
        // de coincidir con el de la BD, que es como se emparejan los auxiliares).
        m.put("ENTIDAD", recortar(ent, 4));
        m.put("UNIDAD", recortar(uni, 5));
        m.put("CODCONT", codcont);
        m.put("CODAUX", aux.getCodAux());
        m.put("NOMAUX", recortar(aux.getNombre(), 60));
        m.put("OBSERV", "");
        m.put("FEULT", java.sql.Date.valueOf(LocalDate.now()));
        m.put("USUAR", recortar(usr != null ? usr : "SISTEMA", 8));
        return m;
    }

    /** Recorta al ancho de la columna del DBF, sin reventar con null. */
    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private Object[] crearRegistroDesdeAuxiliar(Auxiliar aux, String entidadCode, String unidadCode,
                                                String usuario, DBFField[] fields) {
        Object[] record = new Object[fields.length];
        
        // Mapeo de valores
        String ENTIDAD = entidadCode;
        String UNIDAD = unidadCode;
        
        Short CODCONT = null;
        if (aux.getGrupoContable() != null && aux.getGrupoContable().getCodContable() != null) {
            try {
                CODCONT = Short.valueOf(aux.getGrupoContable().getCodContable().toString().trim());
            } catch (Exception e) {
                log.warn("No se pudo convertir CODCONT");
            }
        }
        
        Short CODAUX = aux.getCodAux();
        String NOMAUX = aux.getNombre();
        LocalDate FEULT = LocalDate.now();
        String USUAR = (usuario != null ? usuario : "SISTEMA");
        
        for (int i = 0; i < fields.length; i++) {
            String fieldName = fields[i].getName().toUpperCase();
            
            switch (fieldName) {
                case "ENTIDAD" -> record[i] = ENTIDAD;
                case "UNIDAD" -> record[i] = UNIDAD;
                case "CODCONT" -> record[i] = CODCONT;
                case "CODAUX" -> record[i] = CODAUX;
                case "NOMAUX" -> record[i] = NOMAUX;
                case "OBSERV" -> record[i] = null;
                case "FEULT" -> record[i] = java.sql.Date.valueOf(FEULT);
                case "USUAR" -> record[i] = USUAR;
                default -> {
                    record[i] = null;
                    log.debug("Campo '{}' no mapeado, establecido como null", fieldName);
                }
            }
        }
        
        return record;
    }
}
