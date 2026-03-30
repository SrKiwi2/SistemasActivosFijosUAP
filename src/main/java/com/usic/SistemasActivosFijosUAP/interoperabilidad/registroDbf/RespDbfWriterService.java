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

import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;


@Service
public class RespDbfWriterService {
    private static final Logger log = LoggerFactory.getLogger(RespDbfWriterService.class);
    private final Object respLock = new Object();

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    private static class CampoDbf {
        String name;
        char type; 
        int length;
        int decimals;
    }

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

    public boolean existsByCodResp(Integer codResp, Short codOfic, String entidad, String unidad) {
        verificarConexionDBF();
        synchronized (respLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getRespDbfFile(), "r")) {
                
                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<CampoDbf> fields = leerMetadatosCampos(raf);
                
                // Ubicar offsets de campos clave
                int offEnt = -1, offUni = -1, offOfi = -1, offResp = -1;
                int lenEnt = 0, lenUni = 0, lenOfi = 0, lenResp = 0;
                int currentOffset = 1;

                for (CampoDbf f : fields) {
                    if ("ENTIDAD".equalsIgnoreCase(f.name)) { offEnt = currentOffset; lenEnt = f.length; }
                    if ("UNIDAD".equalsIgnoreCase(f.name))  { offUni = currentOffset; lenUni = f.length; }
                    if ("CODOFIC".equalsIgnoreCase(f.name)) { offOfi = currentOffset; lenOfi = f.length; }
                    if ("CODRESP".equalsIgnoreCase(f.name)) { offResp = currentOffset; lenResp = f.length; } // Ojo: CODRESP suele ser Numeric
                    currentOffset += f.length;
                }

                if (offResp == -1 || offOfi == -1) return false; // Faltan campos clave

                byte[] bufResp = new byte[lenResp];
                byte[] bufOfi = new byte[lenOfi];
                byte[] bufEnt = (offEnt != -1) ? new byte[lenEnt] : null;
                byte[] bufUni = (offUni != -1) ? new byte[lenUni] : null;

                for (int i = 0; i < numRecords; i++) {
                    long pos = headerLen + ((long) i * recordLen);
                    
                    raf.seek(pos);
                    if (raf.readByte() == 0x2A) continue; // Borrado

                    // 1. Verificar CODRESP
                    raf.seek(pos + offResp);
                    raf.read(bufResp);
                    String respStr = new String(bufResp, "CP1252").trim();
                    try {
                        if (respStr.isEmpty() || Double.valueOf(respStr).intValue() != codResp.intValue()) continue;
                    } catch (Exception e) { continue; }

                    // 2. Verificar CODOFIC
                    raf.seek(pos + offOfi);
                    raf.read(bufOfi);
                    String ofiStr = new String(bufOfi, "CP1252").trim();
                    try {
                        if (ofiStr.isEmpty() || Double.valueOf(ofiStr).intValue() != codOfic.intValue()) continue;
                    } catch (Exception e) { continue; }

                    // 3. Verificar ENTIDAD (si aplica)
                    if (offEnt != -1) {
                        raf.seek(pos + offEnt);
                        raf.read(bufEnt);
                        if (!new String(bufEnt, "CP1252").trim().equals(entidad.trim())) continue;
                    }

                    // 4. Verificar UNIDAD (si aplica)
                    if (offUni != -1) {
                        raf.seek(pos + offUni);
                        raf.read(bufUni);
                        if (!new String(bufUni, "CP1252").trim().equals(unidad.trim())) continue;
                    }

                    return true; // ¡Coincidencia total!
                }
                return false;

            } catch (Exception e) {
                log.error("Error verificando existencia RESP: {}", e.getMessage());
                return false;
            }
        }
    }

    /**
     * Inserta un nuevo responsable en RESP.DBF
     */
    public void insertarDesdeResponsable(Responsable resp, String entidadCode, String unidadCode, String usuario) {
        log.info("⚡ Insertando Responsable CODRESP={} (Append Seguro)", resp.getCodigoFuncionario());
        
        synchronized (respLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getRespDbfFile(), "rw");
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
                    // Ignorar contenido de MEMO para no romper punteros .dbt
                    if (field.type != 'M') {
                        valor = obtenerValorCampo(field.name, resp, entidadCode, unidadCode, usuario);
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
                log.info("✅ Responsable insertado correctamente.");

            } catch (Exception e) {
                log.error("Error crítico escribiendo RESP.DBF", e);
                throw new RuntimeException("Error escribiendo en RESP.DBF: " + e.getMessage());
            }
        }
    }

    /**
     * Actualiza un responsable existente en RESP.DBF
     */
    public void actualizarDesdeResponsable(Integer codRespOriginal, Short codOficOriginal, String entidadOriginal, String unidadOriginal,
                                           Responsable resp, String entidadCode, String unidadCode, String usuario) {
        log.info("⚡ Actualizando Responsable CODRESP={} in-place", codRespOriginal);

        synchronized (respLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getRespDbfFile(), "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock lock = channel.lock()) {

                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<CampoDbf> fields = leerMetadatosCampos(raf);
                
                // Ubicar offsets clave
                int offEnt = -1, offUni = -1, offOfi = -1, offResp = -1;
                int lenEnt = 0, lenUni = 0, lenOfi = 0, lenResp = 0;
                int currentOffset = 1;

                for (CampoDbf f : fields) {
                    if ("ENTIDAD".equalsIgnoreCase(f.name)) { offEnt = currentOffset; lenEnt = f.length; }
                    if ("UNIDAD".equalsIgnoreCase(f.name))  { offUni = currentOffset; lenUni = f.length; }
                    if ("CODOFIC".equalsIgnoreCase(f.name)) { offOfi = currentOffset; lenOfi = f.length; }
                    if ("CODRESP".equalsIgnoreCase(f.name)) { offResp = currentOffset; lenResp = f.length; }
                    currentOffset += f.length;
                }

                if (offResp == -1 || offOfi == -1) throw new RuntimeException("Campos clave no encontrados en RESP.DBF");

                long foundPos = -1;
                byte[] bufResp = new byte[lenResp];
                byte[] bufOfi = new byte[lenOfi];
                byte[] bufEnt = (offEnt != -1) ? new byte[lenEnt] : null;
                byte[] bufUni = (offUni != -1) ? new byte[lenUni] : null;

                for (int i = 0; i < numRecords; i++) {
                    long pos = headerLen + ((long) i * recordLen);
                    
                    // 1. Validar CODRESP
                    raf.seek(pos + offResp);
                    raf.read(bufResp);
                    try {
                        String s = new String(bufResp, "CP1252").trim();
                        if (s.isEmpty() || Double.valueOf(s).intValue() != codRespOriginal.intValue()) continue;
                    } catch (Exception e) { continue; }

                    // 2. Validar CODOFIC
                    raf.seek(pos + offOfi);
                    raf.read(bufOfi);
                    try {
                        String s = new String(bufOfi, "CP1252").trim();
                        if (s.isEmpty() || Double.valueOf(s).intValue() != codOficOriginal.intValue()) continue;
                    } catch (Exception e) { continue; }

                    // 3. Validar Entidad
                    if (offEnt != -1) {
                        raf.seek(pos + offEnt);
                        raf.read(bufEnt);
                        if (!new String(bufEnt, "CP1252").trim().equals(entidadOriginal.trim())) continue;
                    }

                    // 4. Validar Unidad
                    if (offUni != -1) {
                        raf.seek(pos + offUni);
                        raf.read(bufUni);
                        if (!new String(bufUni, "CP1252").trim().equals(unidadOriginal.trim())) continue;
                    }

                    foundPos = pos;
                    break;
                }

                if (foundPos == -1) throw new RuntimeException("Responsable no encontrado para actualizar (Clave no coincide)");

                // Sobrescribir (Saltando Memos)
                raf.seek(foundPos + 1); 
                
                for (CampoDbf field : fields) {
                    if (field.type == 'M') {
                        raf.skipBytes(field.length); 
                        continue;
                    }
                    Object valor = obtenerValorCampo(field.name, resp, entidadCode, unidadCode, usuario);
                    byte[] bytes = convertirValorABytes(valor, field);
                    raf.write(bytes);
                }

                actualizarFechaModificacion(raf);
                log.info("✅ Registro actualizado.");

            } catch (Exception e) {
                throw new RuntimeException("Error actualizando RESP.DBF: " + e.getMessage());
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

    private Object obtenerValorCampo(String fieldName, Responsable r, String ent, String uni, String usr) {
        String name = fieldName.toUpperCase();
        return switch (name) {
            case "ENTIDAD" -> ent;
            case "UNIDAD" -> uni;
            case "CODOFIC" -> (r.getOficina() != null) ? r.getOficina().getCodOfi() : null;
            case "CODRESP" -> (r.getCodigoFuncionario() != null) ? Integer.valueOf(r.getCodigoFuncionario().replaceAll("\\D+", "")) : null;
            // ⚠️ Importante: Nombre completo compuesto
            case "NOMRESP" -> construirNombreCompleto(r.getPersona(), 35); // Ajustar longitud según DBF real (usualmente 35-40 chars)
            case "CARGO" -> (r.getCargo() != null) ? r.getCargo().getNombre() : null;
            case "CI" -> (r.getPersona() != null) ? r.getPersona().getCi() : null;
            case "FEULT" -> java.sql.Date.valueOf(LocalDate.now());
            case "USUAR" -> usr;
            case "API_ESTADO" -> r.getApiEstado() != null ? r.getApiEstado() : 1;
            case "COD_EXP" -> r.getCodExp() != null ? r.getCodExp() : 1;
            default -> null; 
        };
    }

    private String construirNombreCompleto(Persona p, int maxLength) {
        if (p == null) return "";
        String s = String.join(" ", 
            p.getNombre() != null ? p.getNombre().trim() : "",
            p.getPaterno() != null ? p.getPaterno().trim() : "",
            p.getMaterno() != null ? p.getMaterno().trim() : ""
        ).trim().toUpperCase();
        
        if (s.length() > maxLength) s = s.substring(0, maxLength).trim();
        return s;
    }

    private byte[] convertirValorABytes(Object value, CampoDbf field) {
        byte[] buffer = new byte[field.length];
        
        // Si es Integer ('I'), se llena con ceros binarios. Si es otro, con espacios ASCII (0x20)
        Arrays.fill(buffer, field.type == 'I' ? (byte) 0x00 : (byte) 0x20); 

        if (value == null) return buffer;

        try {
            if (field.type == 'I') {
                // FoxPro Integer (SmallInt) - 4 bytes Little-Endian
                int intVal = 0;
                if (value instanceof Number n) {
                    intVal = n.intValue();
                } else {
                    try { intVal = Integer.parseInt(value.toString().trim()); } catch (Exception ignored) {}
                }
                
                // Escribir los 4 bytes en formato Little-Endian
                buffer[0] = (byte) (intVal & 0xFF);
                buffer[1] = (byte) ((intVal >> 8) & 0xFF);
                buffer[2] = (byte) ((intVal >> 16) & 0xFF);
                buffer[3] = (byte) ((intVal >> 24) & 0xFF);
                
                return buffer; // Retornamos directo porque ya llenamos el buffer exacto
                
            } else if (field.type == 'N' || field.type == 'F') { 
                String fmt = (field.decimals == 0) ? "%" + field.length + "d" 
                                                : "%" + field.length + "." + field.decimals + "f";
                String numStr;
                if (value instanceof Number n) {
                    numStr = (field.decimals == 0) ? String.format(fmt, n.longValue()) 
                                                : String.format(fmt, n.doubleValue());
                } else {
                    numStr = value.toString();
                }
                byte[] data = numStr.getBytes("CP1252");
                int offset = Math.max(0, field.length - data.length);
                System.arraycopy(data, 0, buffer, offset, Math.min(data.length, field.length));
                
            } else if (field.type == 'D') { 
                if (value instanceof java.sql.Date d) {
                    byte[] data = d.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")).getBytes("CP1252");
                    System.arraycopy(data, 0, buffer, 0, Math.min(data.length, field.length));
                }
            } else { 
                // Cadenas de texto ('C'), Memos ('M')
                byte[] data = value.toString().toUpperCase().getBytes("CP1252");
                System.arraycopy(data, 0, buffer, 0, Math.min(data.length, field.length));
            }

        } catch (Exception e) {
            log.error("Error convirtiendo campo {}: {}", field.name, e.getMessage());
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
