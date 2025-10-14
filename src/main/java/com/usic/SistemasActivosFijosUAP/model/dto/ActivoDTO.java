package com.usic.SistemasActivosFijosUAP.model.dto;

import java.math.BigDecimal;

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
    private BigDecimal vidaUtil;
    private String fechaAdquisicion;
    private String estado;
    private String acciones;   // HTML
    private String idEnc;      // "" cuando viene de DBF
    private String source;  
}
