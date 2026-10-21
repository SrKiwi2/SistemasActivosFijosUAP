package com.usic.SistemasActivosFijosUAP.model.dto.adicional;

import java.util.List;

import lombok.Data;

/** Una operación de alta con varios lotes, cada uno con su propia clasificación y destino. */
@Data
public class AdicionalLotesRequest {
    private String token;
    private List<AdicionalRequest> lotes;
}
