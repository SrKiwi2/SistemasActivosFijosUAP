package com.usic.SistemasActivosFijosUAP.model.dto.responsable;

import lombok.Getter;
import lombok.Setter;

@Setter @Getter
public class ResponsableApiDataDTO {
    // Datos de la Persona
    private String nombre;
    private String paterno;
    private String materno;
    private String ci;
    private String correo;

    // Datos del Cargo (solo para visualización inicial)
    private String nombreCargoApi;
    
    // Indica si ya existe un responsable asociado a esta persona
    private boolean yaEsResponsable;
}
