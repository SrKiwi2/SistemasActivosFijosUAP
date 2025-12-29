package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracionGestionDTO {
    private Integer gestion;
    private String prefijoDocumento;
    private String ciudad;
    private String responsableActivosNombre;
    private String estado;
}
