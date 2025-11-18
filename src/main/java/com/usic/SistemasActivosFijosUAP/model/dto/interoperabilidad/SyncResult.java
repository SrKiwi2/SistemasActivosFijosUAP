package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;


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
}
