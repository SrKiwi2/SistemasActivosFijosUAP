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
public class DbfColaService {

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Encola un INSERT para la tabla indicada.
     *
     * @param tabla  nombre de la tabla DBF (p. ej. "RESP")
     * @param campos pares campo-&gt;valor (valores crudos; se convierten a texto)
     */
    public void encolarInsert(String tabla, Map<String, Object> campos) {
        try {
            Path cola = Path.of(dbfPath, "_cola");
            Files.createDirectories(cola);

            // Convertir cada valor a texto que el worker pueda interpretar
            Map<String, String> camposTexto = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : campos.entrySet()) {
                camposTexto.put(e.getKey(), formatear(e.getValue()));
            }

            Map<String, Object> orden = new LinkedHashMap<>();
            orden.put("tabla", tabla);
            orden.put("op", "INSERT");
            orden.put("campos", camposTexto);

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

            log.info("📤 Orden encolada para VSIAF: {}", fin.getFileName());
        } catch (Exception e) {
            log.error("No se pudo encolar la orden para {}: {}", tabla, e.getMessage(), e);
            throw new RuntimeException("Error encolando orden DBF: " + e.getMessage(), e);
        }
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
