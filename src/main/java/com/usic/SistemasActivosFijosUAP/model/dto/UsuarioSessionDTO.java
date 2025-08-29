package com.usic.SistemasActivosFijosUAP.model.dto;

import java.io.Serializable;

import lombok.Getter;
import lombok.Setter;

@Setter @Getter
public class UsuarioSessionDTO implements Serializable{
    private Long   idUsuario;
    private String username;
    private String rol;         // en mayúsculas
    private Long   personaId;
    private String personaNombre;
}
