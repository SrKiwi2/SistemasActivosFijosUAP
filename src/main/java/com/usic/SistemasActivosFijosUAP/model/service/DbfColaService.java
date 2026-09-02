package com.usic.SistemasActivosFijosUAP.model.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ReferenciaOrdenDbf;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deja "ordenes" en una cola de archivos para que un worker en la VM Windows
 * (PowerShell + VFPOLEDB) las inserte en los DBF del VSIAF manteniendo el indice
 * .CDX automaticamente. Asi el SCIAF deja de escribir bytes crudos (que rompian
 * el indice y obligaban a reindexar).
 *
 * <p>Cada orden es un JSON en {@code <legacy.dbf.path>/_cola/<TABLA>_<ts>_<id>.json}:
 * <pre>
 *   { "tabla":"RESP", "op":"INSERT",
 *     "campos": { "ENTIDAD":"148", "CODRESP":"123", "NOMRESP":"...", ... } }
 * </pre>
 * Los valores van como texto; el worker los formatea segun el tipo real de cada
 * columna. Se escribe primero como {@code .json.tmp} y se renombra a {@code .json}
 * para que el worker nunca lea un archivo a medio escribir.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbfColaService {

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    private final DbfColaRegistroService registroService;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Encola un INSERT para la tabla indicada.
     *
     * @param tabla  nombre de la tabla DBF (p. ej. "RESP")
     * @param campos pares campo-&gt;valor (valores crudos; se convierten a texto)
     * @return nombre del archivo encolado, la llave con la que el worker devuelve el resultado
     */
    public String encolarInsert(String tabla, Map<String, Object> campos) {
        return encolarInsert(tabla, campos, null);
    }

    /** Igual que {@link #encolarInsert(String, Map)}, dejando constancia de a qué apunta. */
    public String encolarInsert(String tabla, Map<String, Object> campos, ReferenciaOrdenDbf ref) {
        Map<String, Object> orden = new LinkedHashMap<>();
        orden.put("tabla", tabla);
        orden.put("op", "INSERT");
        orden.put("campos", aTexto(campos));

        String archivo = escribirOrden(tabla, orden);
        registroService.registrar(archivo, tabla, "INSERT", null, ref);
        return archivo;
    }

    /**
     * Encola un UPDATE para la tabla indicada.
     *
     * @param tabla  nombre de la tabla DBF (p. ej. "RESP")
     * @param clave  pares campo-&gt;valor que identifican el registro (WHERE)
     * @param campos pares campo-&gt;valor a actualizar (SET)
     * @return nombre del archivo encolado, la llave con la que el worker devuelve el resultado
     */
    public String encolarUpdate(String tabla, Map<String, Object> clave, Map<String, Object> campos) {
        return encolarUpdate(tabla, clave, campos, null);
    }

    /** Igual que {@link #encolarUpdate(String, Map, Map)}, dejando constancia de a qué apunta. */
    public String encolarUpdate(String tabla, Map<String, Object> clave,
                                Map<String, Object> campos, ReferenciaOrdenDbf ref) {
        Map<String, String> claveTexto = aTexto(clave);

        Map<String, Object> orden = new LinkedHashMap<>();
        orden.put("tabla", tabla);
        orden.put("op", "UPDATE");
        orden.put("clave", claveTexto);
        orden.put("campos", aTexto(campos));

        String archivo = escribirOrden(tabla, orden);
        registroService.registrar(archivo, tabla, "UPDATE", claveTexto, ref);
        return archivo;
    }

    /**
     * Escribe la orden como JSON en _cola (primero .tmp y luego rename, para lecturas atómicas).
     *
     * @return nombre del archivo, sin ruta
     */
    private String escribirOrden(String tabla, Map<String, Object> orden) {
        try {
            verificarMontaje();

            Path cola = Path.of(dbfPath, "_cola");
            Files.createDirectories(cola);
            // Las carpetas de resultado se crean acá aunque las escriba el worker: si le
            // faltan, su Move-Item falla DESPUÉS de haber aplicado el SQL, el archivo se
            // queda en _cola y la misma orden se vuelve a ejecutar en bucle contra el DBF.
            Files.createDirectories(Path.of(dbfPath, "_hechos"));
            Files.createDirectories(Path.of(dbfPath, "_errores"));

            String nombre = tabla + "_" + System.currentTimeMillis() + "_"
                    + UUID.randomUUID().toString().substring(0, 8);
            Path tmp = cola.resolve(nombre + ".json.tmp");
            Path fin = cola.resolve(nombre + ".json");

            Files.write(tmp, mapper.writeValueAsBytes(orden));
            try {
                Files.move(tmp, fin, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicFail) {
                // Algunos sistemas de archivos (CIFS) no soportan ATOMIC_MOVE
                Files.move(tmp, fin, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("📤 Orden {} encolada para VSIAF: {}", orden.get("op"), fin.getFileName());
            return fin.getFileName().toString();
        } catch (Exception e) {
            log.error("No se pudo encolar la orden para {}: {}", tabla, e.getMessage(), e);
            throw new RuntimeException("Error encolando orden DBF: " + e.getMessage(), e);
        }
    }

    /**
     * Se asegura de que {@code legacy.dbf.path} sea de verdad el share del VSIAF antes de
     * dejar la orden.
     * <p>
     * Si el montaje CIFS se cae, {@code /mnt/dbfwin} sigue existiendo como carpeta local
     * vacía: {@code createDirectories} crea un {@code _cola/} en el disco del servidor, la
     * orden se escribe sin error y el sistema informa que el cambio salió, pero el worker
     * de la VM Windows nunca va a ver ese archivo. La presencia de ACTUAL.DBF es la prueba
     * barata de que la carpeta es la real y no un punto de montaje vacío.
     */
    private void verificarMontaje() {
        Path base = Path.of(dbfPath);
        if (!Files.isDirectory(base)) {
            throw new IllegalStateException("La carpeta del VSIAF (" + dbfPath
                    + ") no está disponible: el montaje está caído.");
        }
        if (!Files.isRegularFile(base.resolve("ACTUAL.DBF"))) {
            throw new IllegalStateException("En " + dbfPath + " no está ACTUAL.DBF: el montaje del "
                    + "VSIAF no está activo. La orden no se encola para no dejarla en una carpeta "
                    + "que el worker no lee.");
        }
    }

    /** Convierte un mapa de valores crudos a texto interpretable por el worker. */
    private Map<String, String> aTexto(Map<String, Object> valores) {
        Map<String, String> txt = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : valores.entrySet()) {
            txt.put(e.getKey(), formatear(e.getValue()));
        }
        return txt;
    }

    /** Convierte un valor a texto interpretable por el worker (fechas yyyy-MM-dd, lógicos 1/0). */
    private String formatear(Object v) {
        if (v == null) return "";
        if (v instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (v instanceof java.time.LocalDate d) return d.toString();
        if (v instanceof Boolean b) return b ? "1" : "0";
        return v.toString().trim();
    }
}
