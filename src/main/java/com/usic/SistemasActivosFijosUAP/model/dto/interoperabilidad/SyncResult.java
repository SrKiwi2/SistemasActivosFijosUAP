package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;


import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResult {
    // ✅ Campos básicos (obligatorios)
    private int totalLeidas;           // Total de registros leídos del DBF
    private int insertados;            // Nuevos registros insertados en BD
    private int actualizados;          // Registros existentes actualizados
    private long duracionMs;           // Duración de la sincronización en milisegundos
    
    // ✅ Campos opcionales (para información detallada)
    private int omitidos;              // Registros omitidos por no tener cambios (hash igual)
    private int sinEntidad;            // Registros sin entidad relacionada
    private int sinPredio;             // Registros sin predio relacionado
    private int sinGrupoContable;      // Registros sin grupo contable
    private int sinOficina;            // Registros sin oficina
    private int duplicadosEnDbf;       // Registros duplicados detectados en el DBF
    private int errores;               // Registros con errores al procesar

    private int sinResponsable;
    
    /**
     * Construye un mensaje descriptivo del resultado
     */
    public String getMensaje() {
        return String.format("Sincronización completada en %.2f segundos", duracionMs / 1000.0);
    }
    
    /**
     * Calcula el total de registros procesados exitosamente
     */
    public int getProcesados() {
        return insertados + actualizados;
    }

    /**
     * ✅ NUEVO: Convierte el resultado a un Map para respuestas HTTP
     * Este método permite enviar el DTO como JSON al frontend
     */
    public Map<String, Object> toResponseMap() {
        Map<String, Object> map = new HashMap<>();
        
        // Campos obligatorios
        map.put("ok", true);
        map.put("totalLeidas", totalLeidas);
        map.put("insertados", insertados);
        map.put("actualizados", actualizados);
        map.put("duracionMs", duracionMs);
        map.put("mensaje", getMensaje());
        
        // Campos opcionales (solo si tienen valor)
        if (omitidos > 0) {
            map.put("omitidos", omitidos);
        }
        if (sinEntidad > 0) {
            map.put("sinEntidad", sinEntidad);
        }
        if (sinPredio > 0) {
            map.put("sinPredio", sinPredio);
        }
        if (sinGrupoContable > 0) {
            map.put("sinGrupoContable", sinGrupoContable);
        }
        if (sinOficina > 0) {
            map.put("sinOficina", sinOficina);
        }
        if (duplicadosEnDbf > 0) {
            map.put("duplicadosEnDbf", duplicadosEnDbf);
        }
        if (errores > 0) {
            map.put("errores", errores);
        }
        
        return map;
    }
    
    /**
     * ✅ ALTERNATIVA: Convierte a Map incluyendo TODOS los campos (incluso con valor 0)
     * Útil cuando el frontend espera todos los campos siempre
     */
    public Map<String, Object> toFullResponseMap() {
        Map<String, Object> map = new HashMap<>();
        
        map.put("ok", true);
        map.put("totalLeidas", totalLeidas);
        map.put("insertados", insertados);
        map.put("actualizados", actualizados);
        map.put("duracionMs", duracionMs);
        map.put("omitidos", omitidos);
        map.put("sinEntidad", sinEntidad);
        map.put("sinPredio", sinPredio);
        map.put("sinGrupoContable", sinGrupoContable);
        map.put("sinOficina", sinOficina);
        map.put("duplicadosEnDbf", duplicadosEnDbf);
        map.put("errores", errores);
        map.put("mensaje", getMensaje());
        
        return map;
    }
}
