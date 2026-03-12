package com.usic.SistemasActivosFijosUAP.model.dto;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

import lombok.Data;

@Data
public class ActivoPendienteItemDTO {
    private Activo activo;
    private String encryptedActivoId;
    private String codigoSnapshot;
}
