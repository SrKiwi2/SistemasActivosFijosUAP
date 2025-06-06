package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ActivoTransferenciaDTO {
    private String codigo;
    private String descripcion;
    private String ubicacionOrigen;
    private String ubicacionActual;
    private String marca;
    private String modelo;
    private String numeroSerie;
    private String dimensiones;
}
