package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class GrupoMetaDTO {
        private Integer vidaUtil;
    private Boolean depreciar;
    private Boolean actualizar;
    public GrupoMetaDTO(Integer vidaUtil, Boolean depreciar, Boolean actualizar) {
        this.vidaUtil = vidaUtil;
        this.depreciar = depreciar;
        this.actualizar = actualizar;
    }
}
