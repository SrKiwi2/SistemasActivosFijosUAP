package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OficinaDTO {
    private Long id;
    private String nombre;
    private Long total;
    private Short codigo;  // ⬅️ NUEVO (opcional para la tabla del perfil)
    private String codigo2; 

    public OficinaDTO() {} // <- constructor vacío
    
    public OficinaDTO(Long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
