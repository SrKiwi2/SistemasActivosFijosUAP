package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResponsableDTO {
    private Long id;
    private String nombre;
    private String codigoFuncionario;

    // ⬇️ NUEVOS (para el perfil)
    private String cargo;
    private String oficina;
    private String oficinaCodigo;

        // ⬇️ Constructor vacío (soluciona "constructor undefined")
        public ResponsableDTO() {}

    public ResponsableDTO(Long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
