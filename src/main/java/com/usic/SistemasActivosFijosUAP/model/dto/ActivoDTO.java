package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ActivoDTO {
    private String index;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String responsable;
    private String oficina;
    private Double costo;
    private Integer vidaUtil;
    private String fechaAdquisicion;
    private String estado;
    private String grupoContable;
    private String acciones;
}
