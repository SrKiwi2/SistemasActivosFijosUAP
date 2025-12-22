package com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;


@Service
public class OficinaDbfWriterService {

    private static final Logger log = LoggerFactory.getLogger(OficinaDbfWriterService.class);
    private final Object oficinaLock = new Object();

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;
    
    // ✅ CLASE INTERNA PARA EVITAR RESTRICCIONES DE LA LIBRERÍA
    private static class CampoDbf {
        String name;
        char type; // 'C', 'N', 'D', 'L', 'M'
        int length;
        int decimals;
    }

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
        synchronized (oficinaLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getOficinaDbfFile(), "r")) {
                
                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<CampoDbf> fields = leerMetadatosCampos(raf);
                
                // Buscar offsets de los 3 campos clave
                int offCod = -1, offEnt = -1, offUni = -1;
                int lenCod = 0, lenEnt = 0, lenUni = 0;
                int currentOffset = 1; // +1 por el byte de borrado

                for (CampoDbf f : fields) {
                    if ("CODOFIC".equalsIgnoreCase(f.name)) { offCod = currentOffset; lenCod = f.length; }
                    if ("ENTIDAD".equalsIgnoreCase(f.name)) { offEnt = currentOffset; lenEnt = f.length; }
                    if ("UNIDAD".equalsIgnoreCase(f.name))  { offUni = currentOffset; lenUni = f.length; }
                    currentOffset += f.length;
                }

                if (offCod == -1) return false;

                byte[] bufCod = new byte[lenCod];
                byte[] bufEnt = (offEnt != -1) ? new byte[lenEnt] : null;
                byte[] bufUni = (offUni != -1) ? new byte[lenUni] : null;

                for (int i = 0; i < numRecords; i++) {
                    long pos = headerLen + ((long) i * recordLen);
                    
                    // Verificar si está borrado
                    raf.seek(pos);
                    if (raf.readByte() == 0x2A) continue; 

                    // 1. Verificar CODOFIC (Lo más rápido primero)
                    raf.seek(pos + offCod);
                    raf.read(bufCod);
                    String codStr = new String(bufCod, "CP1252").trim();

                    try {
                        // Si el código NO coincide, pasamos al siguiente
                        if (codStr.isEmpty() || Integer.parseInt(codStr) != codOfic.intValue()) {
                            continue;
                        }
                    } catch (NumberFormatException e) { continue; }

                    // 2. Verificar ENTIDAD (Si existe la columna)
                    if (offEnt != -1) {
                        raf.seek(pos + offEnt);
                        raf.read(bufEnt);
                        String entStr = new String(bufEnt, "CP1252").trim();
                        if (!entStr.equals(entidad.trim())) continue; // No coincide entidad
                    }

                    // 3. Verificar UNIDAD (Si existe la columna)
                    if (offUni != -1) {
                        raf.seek(pos + offUni);
                        raf.read(bufUni);
                        String uniStr = new String(bufUni, "CP1252").trim();
                        if (!uniStr.equals(unidad.trim())) continue; // No coincide unidad
                    }

                    // ¡Si llegamos aquí, TODO coincide!
                    return true;
                }
                return false;

            } catch (Exception e) {
                log.error("Error verificando existencia: {}", e.getMessage());
                return false;
            }
        }
    }

    /**
     * Inserta un nuevo registro en OFICINA.DBF
     */
    public void insertarDesdeOficina(Oficina oficina, String entidadCode, String unidadCode, String usuario) {
        log.info("⚡ Insertando oficina {} (Append Seguro)", oficina.getCodOfi());
        
        synchronized (oficinaLock) {
            File dbfFile = getOficinaDbfFile();
            
            try (RandomAccessFile raf = new RandomAccessFile(dbfFile, "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock lock = channel.lock()) {

                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<CampoDbf> fields = leerMetadatosCampos(raf);

                byte[] nuevoRegistro = new byte[recordLen];
                Arrays.fill(nuevoRegistro, (byte) 0x20); 
                nuevoRegistro[0] = 0x20; 

                int currentOffset = 1;
                
                for (CampoDbf field : fields) {
                    Object valor = null;
                    // Ignoramos valor para Memos para no romper .dbt
                    if (field.type != 'M') {
                        valor = obtenerValorCampo(field.name, oficina, entidadCode, unidadCode, usuario);
                    }
                    byte[] bytes = convertirValorABytes(valor, field);
                    System.arraycopy(bytes, 0, nuevoRegistro, currentOffset, Math.min(bytes.length, field.length));
                    currentOffset += field.length;
                }

                long posFinal = headerLen + ((long) numRecords * recordLen);
                raf.seek(posFinal);
                raf.write(nuevoRegistro);
                raf.writeByte(0x1A); // EOF

                raf.seek(4);
                raf.writeInt(Integer.reverseBytes(numRecords + 1));

                actualizarFechaModificacion(raf);
                log.info("✅ Registro insertado correctamente.");

            } catch (Exception e) {
                log.error("Error crítico escribiendo DBF", e);
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

        synchronized (oficinaLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getOficinaDbfFile(), "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock lock = channel.lock()) {

                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<CampoDbf> fields = leerMetadatosCampos(raf);
                
                // Ubicar offsets
                int offCod = -1, offEnt = -1, offUni = -1;
                int lenCod = 0, lenEnt = 0, lenUni = 0;
                int currentOffset = 1;

                for (CampoDbf f : fields) {
                    if ("CODOFIC".equalsIgnoreCase(f.name)) { offCod = currentOffset; lenCod = f.length; }
                    if ("ENTIDAD".equalsIgnoreCase(f.name)) { offEnt = currentOffset; lenEnt = f.length; }
                    if ("UNIDAD".equalsIgnoreCase(f.name))  { offUni = currentOffset; lenUni = f.length; }
                    currentOffset += f.length;
                }

                if (offCod == -1) throw new RuntimeException("CODOFIC no encontrado");

                long foundPos = -1;
                byte[] bufCod = new byte[lenCod];
                byte[] bufEnt = (offEnt != -1) ? new byte[lenEnt] : null;
                byte[] bufUni = (offUni != -1) ? new byte[lenUni] : null;

                for (int i = 0; i < numRecords; i++) {
                    long pos = headerLen + ((long) i * recordLen);
                    
                    // 1. Validar Código
                    raf.seek(pos + offCod);
                    raf.read(bufCod);
                    String codStr = new String(bufCod, "CP1252").trim();
                    try {
                        if (codStr.isEmpty() || Integer.parseInt(codStr) != codOficOriginal.intValue()) continue;
                    } catch (Exception e) { continue; }

                    // 2. Validar Entidad
                    if (offEnt != -1) {
                        raf.seek(pos + offEnt);
                        raf.read(bufEnt);
                        String entStr = new String(bufEnt, "CP1252").trim();
                        if (!entStr.equals(entidadOriginal.trim())) continue;
                    }

                    // 3. Validar Unidad
                    if (offUni != -1) {
                        raf.seek(pos + offUni);
                        raf.read(bufUni);
                        String uniStr = new String(bufUni, "CP1252").trim();
                        if (!uniStr.equals(unidadOriginal.trim())) continue;
                    }

                    foundPos = pos;
                    break;
                }

                if (foundPos == -1) throw new RuntimeException("Registro no encontrado para actualizar (Clave no coincide)");

                // Sobrescribir (Saltando Memos)
                raf.seek(foundPos + 1); 
                
                for (CampoDbf field : fields) {
                    if (field.type == 'M') {
                        raf.skipBytes(field.length); 
                        continue;
                    }
                    Object valor = obtenerValorCampo(field.name, oficina, entidadCode, unidadCode, usuario);
                    byte[] bytes = convertirValorABytes(valor, field);
                    raf.write(bytes);
                }

                actualizarFechaModificacion(raf);
                log.info("✅ Registro actualizado.");

            } catch (Exception e) {
                throw new RuntimeException("Error actualizando DBF: " + e.getMessage());
            }
        }
    }

    // ================= HELPER METHODS =================

    private List<CampoDbf> leerMetadatosCampos(RandomAccessFile raf) throws IOException {
        List<CampoDbf> lista = new ArrayList<>();
        raf.seek(8);
        short headerLen = Short.reverseBytes(raf.readShort());
        raf.seek(32); 

        while (raf.getFilePointer() < headerLen - 1) {
            byte[] nameBytes = new byte[11];
            raf.read(nameBytes);
            if (nameBytes[0] == 0x0D) break; 

            CampoDbf c = new CampoDbf();
            c.name = new String(nameBytes, "CP1252").trim();
            c.type = (char) raf.readByte();
            raf.skipBytes(4); 
            c.length = raf.readUnsignedByte();
            c.decimals = raf.readUnsignedByte();
            raf.skipBytes(14); 
            lista.add(c);
        }
        return lista;
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
            default -> null; 
        };
    }

    private byte[] convertirValorABytes(Object value, CampoDbf field) {
        byte[] buffer = new byte[field.length];
        Arrays.fill(buffer, (byte) 0x20); 

        if (value == null) return buffer;

        try {
            byte[] data;
            if (field.type == 'N' || field.type == 'F') { 
                String fmt = (field.decimals == 0) ? "%" + field.length + "d" 
                                                   : "%" + field.length + "." + field.decimals + "f";
                String numStr;
                if (value instanceof Number n) {
                    numStr = (field.decimals == 0) ? String.format(fmt, n.longValue()) 
                                                   : String.format(fmt, n.doubleValue());
                } else {
                    numStr = value.toString();
                }
                data = numStr.getBytes("CP1252");
            } else if (field.type == 'D') { 
                if (value instanceof java.sql.Date d) {
                    data = d.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")).getBytes("CP1252");
                } else {
                    data = new byte[0];
                }
            } else { 
                data = value.toString().toUpperCase().getBytes("CP1252");
            }

            if (field.type == 'N' || field.type == 'F') {
                int offset = Math.max(0, field.length - data.length);
                System.arraycopy(data, 0, buffer, offset, Math.min(data.length, field.length));
            } else {
                System.arraycopy(data, 0, buffer, 0, Math.min(data.length, field.length));
            }

        } catch (Exception e) {
            log.error("Error convirtiendo campo {}", field.name);
        }
        return buffer;
    }

    private void actualizarFechaModificacion(RandomAccessFile raf) throws IOException {
        long posOriginal = raf.getFilePointer();
        raf.seek(1);
        LocalDate now = LocalDate.now();
        raf.writeByte(now.getYear() - 1900);
        raf.writeByte(now.getMonthValue());
        raf.writeByte(now.getDayOfMonth());
        raf.seek(posOriginal);
    }
}