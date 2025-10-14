package com.usic.SistemasActivosFijosUAP.model.dto;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

@Setter @Getter
public class ActivoFormDTO {
    private Long id;
    private String codigo;
    private String descripcion;
    private String fechaAdquisicion; // ISO yyyy-MM-dd
    private BigDecimal vidaUtil;
    private Double costo;

    private Long municipioId;
    private Long predioId;
    private Long grupoContableId;
    private Long auxiliarId;
    private String auxiliarNombre; 
    private Long oficinaId;
    private Long responsableId;
    private String responsableNombre;
    private Long organismoFinancieroId;
    private String organismoFinancieroNombre;
    
}
