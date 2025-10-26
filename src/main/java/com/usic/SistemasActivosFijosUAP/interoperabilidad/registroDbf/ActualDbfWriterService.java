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

import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.linuxense.javadbf.DBFWriter;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

@Service
public class ActualDbfWriterService {

    private static final Logger log = LoggerFactory.getLogger(ActualDbfWriterService.class);
    private final Object actualLock = new Object();

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    private File getActualDbfFile() {
        File dbfFile = new File(dbfPath, "ACTUAL.DBF");
        if (!dbfFile.exists()) {
            throw new RuntimeException(
                String.format("El archivo ACTUAL.DBF no existe en %s", dbfPath)
            );
        }
        if (!dbfFile.canRead() || !dbfFile.canWrite()) {
            throw new RuntimeException(
                String.format("No hay permisos de lectura/escritura en %s", dbfFile.getAbsolutePath())
            );
        }
        return dbfFile;
    }

    /**
     * Verifica que el directorio DBF esté montado y accesible
     */
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
            
            File actualDbf = new File(dir, "ACTUAL.DBF");
            if (!actualDbf.exists()) {
                throw new RuntimeException(
                    String.format("El archivo ACTUAL.DBF no existe en %s", dbfPath)
                );
            }
            
            log.debug("Conexión DBF verificada correctamente: {}", actualDbf.getAbsolutePath());
            
        } catch (Exception e) {
            log.error("No se puede conectar al DBF: {}", e.getMessage());
            throw new RuntimeException(
                "El sistema de archivos DBF no está disponible. " +
                "Verifique que /mnt/dbfwin esté montado correctamente.", e
            );
        }
    }

    /**
     * Verifica si existe un registro con el código dado
     */
    public boolean existsByCodigo(String codigo) {
        verificarConexionDBF();
        
        synchronized (actualLock) {
            try (InputStream fis = new FileInputStream(getActualDbfFile());
                 DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {
                
                int codigoFieldIndex = -1;
                for (int i = 0; i < reader.getFieldCount(); i++) {
                    DBFField field = reader.getField(i);
                    if ("CODIGO".equalsIgnoreCase(field.getName())) {
                        codigoFieldIndex = i;
                        break;
                    }
                }
                
                if (codigoFieldIndex == -1) {
                    throw new RuntimeException("No se encontró el campo CODIGO en ACTUAL.DBF");
                }
                
                Object[] record;
                while ((record = reader.nextRecord()) != null) {
                    if (record[codigoFieldIndex] != null && 
                        codigo.equals(record[codigoFieldIndex].toString().trim())) {
                        return true;
                    }
                }
                
                return false;
                
            } catch (Exception e) {
                log.error("Error verificando existencia de código {}: {}", codigo, e.getMessage());
                throw new RuntimeException("Error accediendo al DBF: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Inserta un nuevo registro en ACTUAL.DBF
     */
    public void insertarDesdeActivo(Activo a, String entidadCode, String unidadCode, String usuario) {
        log.info("Iniciando inserción de activo {} en ACTUAL.DBF", a.getCodigo());
        verificarConexionDBF();
        
        synchronized (actualLock) {
            try {
                File dbfFile = getActualDbfFile();
                File tempFile = new File(dbfFile.getParent(), "ACTUAL_TEMP.DBF");
                
                // Leer todos los registros existentes
                List<Object[]> records = new ArrayList<>();
                DBFField[] fields;
                
                try (InputStream fis = new FileInputStream(dbfFile);
                     DBFReader reader = new DBFReader(fis, Charset.forName("CP1252"))) {
                    
                    fields = new DBFField[reader.getFieldCount()];
                    for (int i = 0; i < reader.getFieldCount(); i++) {
                        fields[i] = reader.getField(i);
                    }
                    
                    Object[] record;
                    while ((record = reader.nextRecord()) != null) {
                        records.add(record);
                    }
                }
                
                // Crear el nuevo registro
                Object[] newRecord = crearRegistroDesdeActivo(a, entidadCode, unidadCode, usuario, fields);
                records.add(newRecord);
                
                // Escribir todos los registros al archivo temporal
                try (OutputStream fos = new FileOutputStream(tempFile);
                     DBFWriter writer = new DBFWriter(fos, Charset.forName("CP1252"))) {
                    
                    writer.setFields(fields);
                    for (Object[] record : records) {
                        writer.addRecord(record);
                    }
                }
                
                // Reemplazar el archivo original con el temporal
                if (!dbfFile.delete()) {
                    throw new IOException("No se pudo eliminar ACTUAL.DBF");
                }
                if (!tempFile.renameTo(dbfFile)) {
                    throw new IOException("No se pudo renombrar ACTUAL_TEMP.DBF a ACTUAL.DBF");
                }
                
                log.info("Activo {} insertado exitosamente en ACTUAL.DBF", a.getCodigo());
                
            } catch (Exception e) {
                log.error("Error insertando activo {} en DBF: {}", a.getCodigo(), e.getMessage(), e);
                throw new RuntimeException(
                    "No se pudo registrar en ACTUAL.DBF: " + e.getMessage(), e
                );
            }
        }
    }
    
/**
     * Crea un arreglo de objetos con los valores del registro a partir del Activo
     */
    private Object[] crearRegistroDesdeActivo(Activo a, String entidadCode, String unidadCode, 
                                              String usuario, DBFField[] fields) {
        Object[] record = new Object[fields.length];
        
        // Mapeo de valores
        String ENTIDAD = entidadCode;
        String UNIDAD = unidadCode;
        String CODIGO = a.getCodigo();
        String CODIGOSEC = a.getCodigoSec();
        String DESCRIP = a.getDescripcion();
        
        Integer CODCONT = null;
        if (a.getGrupoContable() != null && a.getGrupoContable().getCodContable() != null) {
            try {
                CODCONT = Integer.valueOf(a.getGrupoContable().getCodContable().toString().trim());
            } catch (Exception e) {
                log.warn("No se pudo convertir CODCONT para activo {}", CODIGO);
            }
        }
        
        Short CODAUX = null;
        if (a.getAuxiliar() != null && a.getAuxiliar().getCodAux() != null) {
            try {
                CODAUX = Short.valueOf(a.getAuxiliar().getCodAux().toString().trim());
            } catch (Exception e) {
                log.warn("No se pudo convertir CODAUX para activo {}", CODIGO);
            }
        }
        
        String ORG_FIN = (a.getOrganismoFinanciero() != null)
                ? String.valueOf(a.getOrganismoFinanciero().getIdOrganismoFinanciero())
                : null;
        
        Short CODOFIC = null;
        if (a.getOficina() != null && a.getOficina().getCodOfi() != null) {
            try {
                CODOFIC = Short.valueOf(a.getOficina().getCodOfi().toString().trim());
            } catch (Exception e) {
                log.warn("No se pudo convertir CODOFIC para activo {}", CODIGO);
            }
        }
        
        Integer CODRESP = null;
        if (a.getResponsable() != null && a.getResponsable().getPersona() != null) {
            String ci = a.getResponsable().getPersona().getCi();
            if (ci != null) {
                String onlyDigits = ci.replaceAll("\\D+", "");
                if (!onlyDigits.isEmpty()) {
                    try {
                        CODRESP = Integer.valueOf(onlyDigits);
                    } catch (Exception e) {
                        log.warn("No se pudo convertir CODRESP para activo {}", CODIGO);
                    }
                }
            }
        }
        
        Double COSTO = a.getCosto();
        Double DEPACU = 0.0;
        
        Integer VIDAUT = null;
        if (a.getVidaUtil() != null) {
            try {
                VIDAUT = new java.math.BigDecimal(a.getVidaUtil().toString()).intValue();
            } catch (Exception e) {
                log.warn("No se pudo convertir VIDAUT para activo {}", CODIGO);
            }
        }
        
        LocalDate FEC = (a.getFechaAdquisicion() != null) ? a.getFechaAdquisicion() : LocalDate.now();
        Integer DIA = FEC.getDayOfMonth();
        Integer MES = FEC.getMonthValue();
        Integer ANO = FEC.getYear();
        
        Short CODESTADO = 1;
        Short API_ESTADO = 1;
        String USUAR = (usuario != null ? usuario : "SISTEMA");
        String OBSERV = null;
        java.util.Date FEULT = java.sql.Date.valueOf(FEC);
        
        // Llenar el arreglo según el orden de los campos
        for (int i = 0; i < fields.length; i++) {
            String fieldName = fields[i].getName().toUpperCase();
            
            switch (fieldName) {
                case "ENTIDAD" -> record[i] = ENTIDAD;
                case "UNIDAD" -> record[i] = UNIDAD;
                case "CODIGO" -> record[i] = CODIGO;
                case "CODIGOSEC" -> record[i] = CODIGOSEC;
                case "DESCRIP" -> record[i] = DESCRIP;
                case "COSTO" -> record[i] = COSTO;
                case "DEPACU" -> record[i] = DEPACU;
                case "VIDAUTIL" -> record[i] = VIDAUT;
                case "MES" -> record[i] = MES;
                case "ANO" -> record[i] = ANO;
                case "DIA" -> record[i] = DIA;
                case "CODOFIC" -> record[i] = CODOFIC;
                case "CODRESP" -> record[i] = CODRESP;
                case "OBSERV" -> record[i] = OBSERV;
                case "CODCONT" -> record[i] = CODCONT;
                case "CODAUX" -> record[i] = CODAUX;
                case "ORG_FIN" -> record[i] = ORG_FIN;
                case "FEULT" -> record[i] = FEULT;
                case "USUAR" -> record[i] = USUAR;
                case "API_ESTADO" -> record[i] = API_ESTADO;
                case "CODESTADO" -> record[i] = CODESTADO;
                default -> record[i] = null; // Campos no mapeados
            }
        }
        
        return record;
    }

}
