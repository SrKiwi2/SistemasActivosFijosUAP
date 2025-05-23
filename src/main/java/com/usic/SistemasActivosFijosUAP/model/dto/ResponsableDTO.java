package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResponsableDTO {
    private Long id;
    private String nombre;

    public ResponsableDTO(Long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
