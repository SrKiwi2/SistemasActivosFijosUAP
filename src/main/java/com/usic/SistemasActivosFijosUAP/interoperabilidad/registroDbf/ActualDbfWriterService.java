package com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.linuxense.javadbf.DBFField;
import com.linuxense.javadbf.DBFReader;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

@Service
public class ActualDbfWriterService {

    private static final Logger log = LoggerFactory.getLogger(ActualDbfWriterService.class);
    private final Object actualLock = new Object();

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    private static class CampoDbf {
        String name;
        char type;
        int length;
        int decimals;
    }

    public ActualDbfWriterService() {
        log.info("Inicializando ActualDbfWriterService (Activos Fijos)");
    }

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

        if (codigo == null) return false;
        
        synchronized (actualLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getActualDbfFile(), "r")) {
                
                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<CampoDbf> fields = leerMetadatosCampos(raf);

                
                int offCodigo = -1;
                int lenCodigo = 0;
                int currentOffset = 1; // Byte 0 es borrado

                for (CampoDbf f : fields) {
                    if ("CODIGO".equalsIgnoreCase(f.name)) {
                        offCodigo = currentOffset;
                        lenCodigo = f.length;
                    }
                    currentOffset += f.length;
                }

                if (offCodigo == -1) return false;

                byte[] bufCodigo = new byte[lenCodigo];
                
                for (int i = 0; i < numRecords; i++) {
                    long pos = headerLen + ((long) i * recordLen);
                    raf.seek(pos);
                    if (raf.readByte() == 0x2A) continue; // Registro borrado (*)

                    raf.seek(pos + offCodigo);
                    raf.read(bufCodigo);
                    String codigoDbf = new String(bufCodigo, "CP1252").trim();

                    if (codigoDbf.equalsIgnoreCase(codigo.trim())) {
                        return true;
                    }
                }
                
                return false;
                
            } catch (Exception e) {
                log.error("Error verificando existencia en DBF", e);
                return false;
            }
        }
    }

    /**
     * Inserta un nuevo registro en ACTUAL.DBF
     */
    public void insertarDesdeActivo(Activo a, String entidadCode, String unidadCode, String usuario) {
        log.info("Iniciando inserción de activo {} en ACTUAL.DBF", a.getCodigo());

        synchronized (actualLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getActualDbfFile(), "rw");
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
                    // Omitir escritura en campos MEMO para no corromper el .DBT
                    if (field.type == 'M') {
                        currentOffset += field.length;
                        continue;
                    }

                    Object valor = obtenerValorCampo(field.name, a, entidadCode, unidadCode, usuario);
                    byte[] bytes = convertirValorABytes(valor, field);
                    
                    // Escribir en el buffer en la posición correcta
                    System.arraycopy(bytes, 0, nuevoRegistro, currentOffset, Math.min(bytes.length, field.length));
                    currentOffset += field.length;
                }

                long posFinal = headerLen + ((long) numRecords * recordLen);
                raf.seek(posFinal);
                raf.write(nuevoRegistro);
                raf.writeByte(0x1A);

                raf.seek(4);
                raf.writeInt(Integer.reverseBytes(numRecords + 1));

                actualizarFechaModificacion(raf);
                log.info("✅ Activo insertado correctamente en DBF.");
                
            } catch (Exception e) {
                log.error("Error crítico escribiendo ACTUAL.DBF", e);
                throw new RuntimeException("Error IO DBF: " + e.getMessage());
            }
        }
    }

    /**
     * Actualiza un registro existente en ACTUAL.DBF buscándolo por código
     */
    public void actualizarDesdeActivo(String codigoOriginal, Activo a, String entidadCode, 
                                      String unidadCode, String usuario) {
        log.info("⚡ Actualizando Activo {} en ACTUAL.DBF", codigoOriginal);
        synchronized (actualLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getActualDbfFile(), "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock lock = channel.lock()) {
                
                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<CampoDbf> fields = leerMetadatosCampos(raf);

                int offCodigo = -1;
                int lenCodigo = 0;
                int currentOffset = 1;

                for (CampoDbf f : fields) {
                    if ("CODIGO".equalsIgnoreCase(f.name)) {
                        offCodigo = currentOffset;
                        lenCodigo = f.length;
                    }
                    currentOffset += f.length;
                }

                if (offCodigo == -1) throw new RuntimeException("Campo CODIGO no encontrado en DBF");

                long foundPos = -1;
                byte[] bufCodigo = new byte[lenCodigo];

                for (int i = 0; i < numRecords; i++) {
                    long pos = headerLen + ((long) i * recordLen);
                    
                    // Verificar si está borrado (0x2A = asterisco = borrado)
                    raf.seek(pos);
                    if (raf.readByte() == 0x2A) continue;

                    // Leer código
                    raf.seek(pos + offCodigo);
                    raf.read(bufCodigo);
                    String codigoDbf = new String(bufCodigo, "CP1252").trim();

                    if (codigoDbf.equalsIgnoreCase(codigoOriginal.trim())) {
                        foundPos = pos;
                        break;
                    }
                }

                if (foundPos == -1) {
                    throw new RuntimeException("Activo con código " + codigoOriginal + " no encontrado en DBF.");
                }

                currentOffset = 1;

                for (CampoDbf field : fields) {
                    // Calcular posición exacta de este campo
                    long fieldPos = foundPos + currentOffset;
                    
                    if (field.type == 'M') {
                        // Saltamos campo Memo, NO LO TOCAMOS
                        currentOffset += field.length;
                        continue;
                    }

                    Object valor = obtenerValorCampo(field.name, a, entidadCode, unidadCode, usuario);
                    byte[] bytes = convertirValorABytes(valor, field);

                    raf.seek(fieldPos);
                    raf.write(bytes); // Sobrescribimos solo los bytes de este campo

                    currentOffset += field.length;
                }

                actualizarFechaModificacion(raf);
                log.info("✅ Activo actualizado correctamente en DBF.");

            } catch (Exception e) {
                throw new RuntimeException("Error actualizando ACTUAL.DBF: " + e.getMessage());
            }
        }
    }

    public void actualizarLoteTransferencias(List<Activo> activos, String entidadCode, String unidadCode, String usuario) {
        if (activos == null || activos.isEmpty()) return;

        // 1. Diccionario para búsqueda O(1)
        Map<String, Activo> mapaActivos = new HashMap<>();
        for (Activo a : activos) {
            mapaActivos.put(a.getCodigo().toUpperCase().trim(), a);
        }

        log.info("⚡ Iniciando actualización masiva OPTIMIZADA en DBF para {} activos", mapaActivos.size());

        synchronized (actualLock) {
            try (RandomAccessFile raf = new RandomAccessFile(getActualDbfFile(), "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock lock = channel.lock()) {

                raf.seek(4);
                int numRecords = Integer.reverseBytes(raf.readInt());
                short headerLen = Short.reverseBytes(raf.readShort());
                short recordLen = Short.reverseBytes(raf.readShort());

                List<CampoDbf> fields = leerMetadatosCampos(raf);

                // Ubicar campos clave
                int offCodigo = -1, lenCodigo = 0;
                int currentOffset = 1;

                Map<String, Integer> fieldOffsets = new HashMap<>();
                
                for (CampoDbf f : fields) {
                    if ("CODIGO".equalsIgnoreCase(f.name)) {
                        offCodigo = currentOffset;
                        lenCodigo = f.length;
                    }
                    fieldOffsets.put(f.name.toUpperCase(), currentOffset);
                    currentOffset += f.length;
                }

                if (offCodigo == -1) throw new RuntimeException("Campo CODIGO no encontrado en DBF");

                // PRE-CALCULAR los campos que vamos a actualizar para no usar .stream() en el bucle
                String[] nombresCamposObjetivo = {"CODOFIC", "CODRESP", "ENTIDAD", "UNIDAD", "USU_MOD", "FEC_MOD", "FEULT"};
                List<CampoDbf> camposAActualizar = new ArrayList<>();
                for (String nc : nombresCamposObjetivo) {
                    for (CampoDbf f : fields) {
                        if (f.name.equalsIgnoreCase(nc) && f.type != 'M') {
                            camposAActualizar.add(f);
                            break;
                        }
                    }
                }

                int actualizados = 0;
                byte[] recordBuffer = new byte[recordLen]; // Buffer para leer toda la fila de un golpe

                // 2. Escaneo secuencial (Carga a RAM en bloques)
                for (int i = 0; i < numRecords; i++) {
                    if (mapaActivos.isEmpty()) break; // Si ya actualizamos todos, salir temprano

                    long pos = headerLen + ((long) i * recordLen);
                    raf.seek(pos);
                    
                    // LECTURA MAGISTRAL: Leemos la fila COMPLETA de un solo viaje a la red
                    raf.readFully(recordBuffer);

                    // Byte 0 es el flag de borrado (0x2A = '*')
                    if (recordBuffer[0] == 0x2A) continue; 

                    // Extraer código directamente del buffer en memoria RAM
                    String codigoDbf = new String(recordBuffer, offCodigo, lenCodigo, "CP1252").trim().toUpperCase();

                    // Si el código está en nuestro lote
                    if (mapaActivos.containsKey(codigoDbf)) {
                        Activo activoModificado = mapaActivos.get(codigoDbf);
                        boolean modificado = false;

                        // Modificamos el buffer en la RAM
                        for (CampoDbf field : camposAActualizar) {
                            int offsetCampo = fieldOffsets.get(field.name.toUpperCase());
                            Object valor = obtenerValorCampo(field.name, activoModificado, entidadCode, unidadCode, usuario);
                            byte[] bytes = convertirValorABytes(valor, field);

                            // Copiamos los bytes nuevos al buffer de la fila (en memoria)
                            System.arraycopy(bytes, 0, recordBuffer, offsetCampo, Math.min(bytes.length, field.length));
                            modificado = true;
                        }

                        // ESCRITURA MAGISTRAL: Escribimos toda la fila modificada de un solo golpe
                        if (modificado) {
                            raf.seek(pos); // Volvemos al inicio de la fila
                            raf.write(recordBuffer);
                        }

                        mapaActivos.remove(codigoDbf);
                        actualizados++;
                    }
                }

                // 3. Actualizar cabecera del DBF
                actualizarFechaModificacion(raf);
                log.info("✅ Lote DBF finalizado. Activos actualizados: {}", actualizados);

            } catch (Exception e) {
                throw new RuntimeException("Error en actualización por lotes DBF: " + e.getMessage());
            }
        }
    }

    private Object obtenerValorCampo(String fieldName, Activo a, String ent, String uni, String usr) {
        String name = fieldName.toUpperCase().trim();
        
        return switch (name) {
            // Identificación
            case "ENTIDAD" -> ent;
            case "UNIDAD"  -> uni;
            case "CODIGO"  -> a.getCodigo();
            case "CODIGOSEC" -> a.getCodigoSec();
            case "DESCRIP" -> limpiarTexto(a.getDescripcion(), 250); // Limite seguro

            // Relaciones Numéricas
            case "CODCONT" -> (a.getGrupoContable() != null) ? a.getGrupoContable().getCodContable() : 0;
            case "CODAUX"  -> (a.getAuxiliar() != null) ? a.getAuxiliar().getCodAux() : 0;
            case "CODOFIC" -> (a.getOficina() != null) ? a.getOficina().getCodOfi() : 0;
            
            // CODRESP: Extraer solo números
            case "CODRESP" -> {
                if (a.getResponsable() != null && a.getResponsable().getCodigoFuncionario() != null) {
                    try {
                        String digits = a.getResponsable().getCodigoFuncionario().replaceAll("\\D+", "");
                        yield digits.isEmpty() ? 0 : Integer.valueOf(digits);
                    } catch(Exception e) { yield 0; }
                }
                yield 0;
            }

            // Valores Monetarios / Numéricos
            case "VIDAUTIL" -> (a.getVidaUtil() != null) ? a.getVidaUtil().doubleValue() : 0.0;
            case "COSTO"    -> (a.getCosto() != null) ? a.getCosto() : 0.0;
            case "DEPACU"   -> (a.getDepreciacionAcum() != null) ? a.getDepreciacionAcum() : 0.0;

            // Fechas desglosadas
            case "DIA" -> (a.getFechaAdquisicion() != null) ? a.getFechaAdquisicion().getDayOfMonth() : 0;
            case "MES" -> (a.getFechaAdquisicion() != null) ? a.getFechaAdquisicion().getMonthValue() : 0;
            case "ANO" -> (a.getFechaAdquisicion() != null) ? a.getFechaAdquisicion().getYear() : 0;
            
            // Booleanos (Lógicos)
            case "B_REV"    -> Boolean.TRUE.equals(a.getRevaluado());
            case "BAND_UFV" -> Boolean.TRUE.equals(a.getBandUfv());

            // Otros Textos
            case "USUAR"    -> usr;
            case "USU_MOD"  -> usr;
            case "BANDERAS" -> a.getBanderas();
            case "NRO_CONV" -> a.getNroConv();
            case "COD_RUBE" -> a.getCodRube();
            case "ORG_FIN"  -> a.getOrgFinCode(); 

            // Fechas completas
            case "FEULT"   -> java.sql.Date.valueOf(LocalDate.now());
            case "FEC_MOD" -> java.sql.Date.valueOf(LocalDate.now());

            // Estados
            case "CODESTADO"  -> 1; // Por defecto BUENO/ACTIVO
            case "API_ESTADO" -> (a.getApiEstado() != null) ? a.getApiEstado() : 1;

            // Valores anteriores (Inicializar en 0 para evitar nulls sucios)
            case "DIA_ANT", "MES_ANT", "ANO_ANT", "VUT_ANT", "COSTO_ANT" -> 0;

            default -> null; 
        };
    }

    private byte[] convertirValorABytes(Object value, CampoDbf field) {
        byte[] buffer = new byte[field.length];
        Arrays.fill(buffer, (byte) 0x20); // Rellenar con espacios

        if (value == null) return buffer;

        try {
            byte[] data;
            
            // 1. LÓGICOS (Booleanos) - 'T' / 'F'
            if (field.type == 'L') {
                boolean b = (Boolean) value;
                buffer[0] = (byte) (b ? 'T' : 'F');
                return buffer;
            }

            // 2. NUMÉRICOS (N, F)
            if (field.type == 'N' || field.type == 'F') {
                // Formateo con Locale.US para usar punto decimal (.)
                String fmt = (field.decimals == 0) 
                    ? "%" + field.length + "d" 
                    : "%" + field.length + "." + field.decimals + "f";
                
                String numStr;
                if (value instanceof Number n) {
                    if (field.decimals == 0) numStr = String.format(fmt, n.longValue());
                    else numStr = String.format(Locale.US, fmt, n.doubleValue());
                } else {
                    numStr = value.toString();
                }
                
                // Alineación derecha para números
                data = numStr.getBytes("CP1252");
                int offset = Math.max(0, field.length - data.length);
                System.arraycopy(data, 0, buffer, offset, Math.min(data.length, field.length));
                return buffer;
            }

            // 3. FECHAS (D) - YYYYMMDD
            if (field.type == 'D') {
                if (value instanceof java.sql.Date d) {
                    data = d.toLocalDate().format(DateTimeFormatter.BASIC_ISO_DATE).getBytes("CP1252");
                } else {
                    data = new byte[0];
                }
                System.arraycopy(data, 0, buffer, 0, Math.min(data.length, field.length));
                return buffer;
            }

            // 4. TEXTO (C)
            String sVal = value.toString().toUpperCase(); 
            // Eliminar caracteres raros si se desea
            data = sVal.getBytes("CP1252");
            System.arraycopy(data, 0, buffer, 0, Math.min(data.length, field.length));
            return buffer;

        } catch (Exception e) {
            log.error("Error convirtiendo campo {} (tipo {}) valor {}", field.name, field.type, value);
        }
        return buffer;
    }

    private String limpiarTexto(String input, int maxLen) {
        if (input == null) return "";
        String s = input.trim().toUpperCase().replace("\n", " ").replace("\r", "");
        if (s.length() > maxLen) return s.substring(0, maxLen);
        return s;
    }

    private List<CampoDbf> leerMetadatosCampos(RandomAccessFile raf) throws IOException {
        List<CampoDbf> lista = new ArrayList<>();
        raf.seek(8);
        short headerLen = Short.reverseBytes(raf.readShort());
        raf.seek(32); // Inicio descriptores de campo

        while (raf.getFilePointer() < headerLen - 1) {
            byte[] nameBytes = new byte[11];
            raf.read(nameBytes);
            if (nameBytes[0] == 0x0D) break; // Fin de cabecera

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