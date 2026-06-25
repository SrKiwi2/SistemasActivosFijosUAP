package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;

import lombok.Data;

@Data
public class AsignacionPendienteDTO {
    private AsignacionActivo asignacion;
    private String encryptedAsignacionId;
    private List<ActivoPendienteItemDTO> items;
    private int totalActivos;
    private long totalSincronizados;
    private long totalPendientes;

    /** Activos PENDIENTES que aún tienen datos obligatorios incompletos. */
    public long getTotalIncompletos() {
        return items == null ? 0 : items.stream().filter(i -> !i.isCompleto()).count();
    }

    /** Activos PENDIENTES completos y listos para subir al VSIAF. */
    public long getTotalListosParaSubir() {
        return items == null ? 0 : items.stream().filter(ActivoPendienteItemDTO::isCompleto).count();
    }
}
