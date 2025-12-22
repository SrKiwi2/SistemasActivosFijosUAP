package com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Omite automáticamente campos MEMO (como OBSERV) que no son soportados para
 * escritura.
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
                    String.format("El archivo OFICINA.DBF no existe en %s", dbfPath));
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

            File oficinaDbf = new File(dir, "OFICINA.DBF");
            if (!oficinaDbf.exists()) {
                throw new RuntimeException(
                        String.format("El archivo OFICINA.DBF no existe en %s", dbfPath));
            }

            log.debug("Conexión DBF verificada correctamente: {}", oficinaDbf.getAbsolutePath());

        } catch (Exception e) {
            log.error("No se puede conectar al DBF: {}", e.getMessage());
            throw new RuntimeException(
                    "El sistema de archivos DBF no está disponible. " +
                            "Verifique que /mnt/dbfwin esté montado correctamente.",
                    e);
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

                int idxCODOFIC = -1, idxENT = -1, idxUNI = -1;
                for (int i = 0; i < reader.getFieldCount(); i++) {
                    String name = reader.getField(i).getName().toUpperCase();
                    if ("CODOFIC".equals(name)) idxCODOFIC = i;
                    if ("ENTIDAD".equals(name)) idxENT = i;
                    if ("UNIDAD".equals(name)) idxUNI = i;
                }

                Object[] record;
                while ((record = reader.nextRecord()) != null) {
                    try {

                        boolean match = true;

                        if (idxCODOFIC >= 0 && record[idxCODOFIC] != null) {
                            Number val = (Number) record[idxCODOFIC];
                            if (val.shortValue() != codOfic.shortValue()) match = false;
                        }

                        if (match && idxENT >= 0 && record[idxENT] != null) {
                            String val = record[idxENT].toString().trim();
                            if (!val.equals(entidad)) match = false;
                        }

                        if (match && idxUNI >= 0 && record[idxUNI] != null) {
                            String val = record[idxUNI].toString().trim();
                            if (!val.equals(unidad)) match = false;
                        }

                        if (match) return true;

                    } catch (Exception e) {
                        log.warn("⚠️ Registro corrupto detectado al verificar existencia (saltando): {}", e.getMessage());
                    }
                }

                return false;

            } catch (Exception e) {
                log.error("Error fatal verificando existencia: {}", e.getMessage());
                throw new RuntimeException("Error leyendo DBF: " + e.getMessage(), e);
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
            File dbfFile = getOficinaDbfFile();
            try (RandomAccessFile raf = new RandomAccessFile(dbfFile, "rw");
                FileChannel channel = raf.getChannel();
                FileLock lock = channel.lock()) {
                
                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<DBFField> fields = leerMetadatosCampos(raf);

                byte[] nuevoRegistro = new byte[recordLen];
                Arrays.fill(nuevoRegistro, (byte) 0x20);

                nuevoRegistro[0] = 0x20;

                int currentOffset = 1;

                for (DBFField field : fields) {
                    Object valor = obtenerValorCampo(field.getName(), oficina, entidadCode, unidadCode, usuario);
                    
                    // IMPORTANTE: Para MEMO, escribimos 10 espacios (puntero vacío) si es nuevo
                    // Esto evita corromper el archivo .DBT
                    if (field.getType() == DBFDataType.MEMO) {
                        valor = null; 
                    }

                    byte[] valorBytes = convertirValorABytes(valor, field);
                    System.arraycopy(valorBytes, 0, nuevoRegistro, currentOffset, Math.min(valorBytes.length, field.getLength()));
                    
                    currentOffset += field.getLength();
                }

                long posFinal = headerLen + ((long) numRecords * recordLen);
                raf.seek(posFinal);
                raf.write(nuevoRegistro);

                raf.writeByte(0x1A);

                raf.seek(4);
                raf.writeInt(Integer.reverseBytes(numRecords + 1));

                actualizarFechaModificacion(raf);

                log.info("✅ Registro añadido correctamente. Total registros: {}", numRecords + 1);

            } catch (Exception e) {
                log.error("Error crítico escribiendo DBF: {}", e.getMessage(), e);
                throw new RuntimeException("Error escribiendo en DBF: " + e.getMessage());
            }
        }
    }

    /**
     * Actualiza un registro existente en OFICINA.DBF
     */
    public void actualizarDesdeOficina(Short codOficOriginal, String entidadOriginal, String unidadOriginal,
            Oficina oficina, String entidadCode, String unidadCode, String usuario) {
        log.info("⚡ Actualizando oficina {} in-place", codOficOriginal);
        verificarConexionDBF();

        synchronized (oficinaLock) {
            File dbfFile = getOficinaDbfFile();
            try (RandomAccessFile raf = new RandomAccessFile(dbfFile, "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock lock = channel.lock()) {

                // 1. Leer Header
                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<DBFField> fields = leerMetadatosCampos(raf);
                
                // Buscar índice del campo CODOFIC para localizar el registro
                int offsetCodOfic = 1; // +1 por el byte de borrado
                int lenCodOfic = 0;
                boolean foundField = false;
                
                for (DBFField f : fields) {
                    if ("CODOFIC".equalsIgnoreCase(f.getName())) {
                        lenCodOfic = f.getLength();
                        foundField = true;
                        break;
                    }
                    offsetCodOfic += f.getLength();
                }

                if (!foundField) throw new RuntimeException("Campo CODOFIC no encontrado en estructura DBF");

                // 2. Buscar el registro secuencialmente (Leyendo solo el CODOFIC para rapidez)
                long foundPos = -1;
                byte[] bufferId = new byte[lenCodOfic];
                
                for (int i = 0; i < numRecords; i++) {
                    long recordPos = headerLen + ((long) i * recordLen);
                    
                    // Saltar al campo CODOFIC de este registro
                    raf.seek(recordPos + offsetCodOfic); 
                    raf.read(bufferId);
                    
                    String idStr = new String(bufferId, "CP1252").trim();
                    try {
                        if (!idStr.isEmpty() && Integer.parseInt(idStr) == codOficOriginal.intValue()) {
                            foundPos = recordPos;
                            break;
                        }
                    } catch (NumberFormatException e) { /* Ignorar basura */ }
                }

                if (foundPos == -1) {
                    throw new RuntimeException("Registro no encontrado para actualizar.");
                }

                // 3. Sobrescribir campos (MENOS MEMO)
                raf.seek(foundPos + 1); // +1 para saltar flag de borrado
                
                for (DBFField field : fields) {
                    // ⚠️ CRÍTICO: NO TOCAR CAMPOS MEMO AL ACTUALIZAR
                    // Si sobrescribimos el puntero MEMO, rompemos la relación con .DBT
                    if (field.getType() == DBFDataType.MEMO) {
                        raf.skipBytes(field.getLength());
                        continue;
                    }

                    Object valor = obtenerValorCampo(field.getName(), oficina, entidadCode, unidadCode, usuario);
                    byte[] valorBytes = convertirValorABytes(valor, field);
                    raf.write(valorBytes);
                }

                actualizarFechaModificacion(raf);
                log.info("✅ Registro actualizado in-place en posición {}", foundPos);

            } catch (Exception e) {
                log.error("Error actualizando DBF: {}", e.getMessage(), e);
                throw new RuntimeException("Error actualizando DBF: " + e.getMessage());
            }
        }
    }

    // ================= HELPER METHODS =================

    private List<DBFField> leerMetadatosCampos(RandomAccessFile raf) throws IOException {
        List<DBFField> fields = new ArrayList<>();
        raf.seek(8);
        short headerLen = Short.reverseBytes(raf.readShort());
        raf.seek(32); // Primer descriptor de campo empieza en byte 32

        while (raf.getFilePointer() < headerLen - 1) {
            byte[] nameBytes = new byte[11];
            raf.read(nameBytes);
            if (nameBytes[0] == 0x0D) break; // Fin del header

            String name = new String(nameBytes, "CP1252").trim();
            byte typeByte = raf.readByte();
            raf.skipBytes(4); // Displacement
            int length = raf.readUnsignedByte();
            int decimals = raf.readUnsignedByte(); // Leemos el byte, pero no siempre lo usamos
            raf.skipBytes(14); // Reserved

            DBFField field = new DBFField();
            field.setName(name);
            
            DBFDataType type = matchType(typeByte);
            field.setType(type);
            field.setLength(length);

            // ✅ CORRECCIÓN: Solo asignar decimales si es Numérico o Flotante
            if (type == DBFDataType.NUMERIC || type == DBFDataType.FLOATING_POINT) {
                field.setDecimalCount(decimals);
            }

            fields.add(field);
        }
        return fields;
    }

    private DBFDataType matchType(byte b) {
        return switch ((char) b) {
            case 'C' -> DBFDataType.CHARACTER;
            case 'N' -> DBFDataType.NUMERIC;
            case 'D' -> DBFDataType.DATE;
            case 'L' -> DBFDataType.LOGICAL;
            case 'M' -> DBFDataType.MEMO;
            default -> DBFDataType.CHARACTER;
        };
    }

    private Object obtenerValorCampo(String fieldName, Oficina o, String ent, String uni, String usr) {
        String name = fieldName.toUpperCase();
        return switch (name) {
            case "ENTIDAD" -> ent;
            case "UNIDAD" -> uni;
            case "CODOFIC" -> o.getCodOfi();
            case "NOMOFIC" -> o.getNombre();
            case "FEULT" -> java.sql.Date.valueOf(LocalDate.now());
            case "USUAR" -> usr;
            case "API_ESTADO" -> o.getApiEstado() != null ? o.getApiEstado() : 1;
            default -> null; // OBSERV se ignora aquí si es memo
        };
    }

    private byte[] convertirValorABytes(Object value, DBFField field) {
        int length = field.getLength();
        byte[] buffer = new byte[length];
        Arrays.fill(buffer, (byte) 0x20); // Espacios

        if (value == null) return buffer;

        try {
            byte[] data;
            if (field.getType() == DBFDataType.NUMERIC || field.getType() == DBFDataType.FLOATING_POINT) {
                // Números alineados a la derecha
                String fmt = "%" + length + "." + field.getDecimalCount() + "f";
                if (field.getDecimalCount() == 0) fmt = "%" + length + "d";
                
                String numStr;
                if (value instanceof Integer || value instanceof Short) {
                    numStr = String.format("%" + length + "d", ((Number) value).longValue());
                } else {
                    numStr = String.format("%" + length + "." + field.getDecimalCount() + "f", ((Number) value).doubleValue());
                }
                data = numStr.getBytes("CP1252");
            } else if (field.getType() == DBFDataType.DATE) {
                // YYYYMMDD
                if (value instanceof java.sql.Date d) {
                    String dateStr = d.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    data = dateStr.getBytes("CP1252");
                } else {
                    data = new byte[0];
                }
            } else {
                // Texto alineado a la izquierda
                String s = value.toString();
                data = s.getBytes("CP1252");
            }

            // Copiar al buffer respetando límites
            if (field.getType() == DBFDataType.NUMERIC) {
                // Copiar derecha a izquierda o directo si el formato ya rellenó espacios
                System.arraycopy(data, 0, buffer, 0, Math.min(data.length, length));
            } else {
                System.arraycopy(data, 0, buffer, 0, Math.min(data.length, length));
            }

        } catch (Exception e) {
            log.error("Error convirtiendo campo {}", field.getName());
        }
        return buffer;
    }

    private void actualizarFechaModificacion(RandomAccessFile raf) throws IOException {
        raf.seek(1);
        LocalDate now = LocalDate.now();
        raf.writeByte(now.getYear() - 1900);
        raf.writeByte(now.getMonthValue());
        raf.writeByte(now.getDayOfMonth());
    }
}