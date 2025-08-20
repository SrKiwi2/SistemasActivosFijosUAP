package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Setter @Getter
@AllArgsConstructor
public class ActivoResponsableDTO {
    private String codigo;
    private String descripcion;
    private Long oficinaId;
    private String oficinaNombre;
    private String responsableNombre;
    private Long responsableId;
}
