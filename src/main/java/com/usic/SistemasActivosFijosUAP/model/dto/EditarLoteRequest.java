package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EditarLoteRequest {
    private List<EditarActivoPendienteRequest> activos;
}
