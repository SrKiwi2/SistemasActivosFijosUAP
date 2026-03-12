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
}
