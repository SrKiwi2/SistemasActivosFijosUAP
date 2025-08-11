package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ActivoConsultaDTO {
    private String codigo;
    private String descripcion;
    private Long idOficina;
    private String nombre;
    private String oficinaTexto;

    public ActivoConsultaDTO(String codigo, String descripcion, Long idOficina, String nombre, String oficinaTexto) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.idOficina = idOficina;
        this.nombre = nombre;
        this.oficinaTexto = oficinaTexto;
    }
}
