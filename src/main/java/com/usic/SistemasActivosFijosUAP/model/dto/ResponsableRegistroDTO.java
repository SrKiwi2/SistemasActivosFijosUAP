package com.usic.SistemasActivosFijosUAP.model.dto;

import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ResponsableRegistroDTO {
    private String codigoFuncionario;
    private String nombreCompleto;
    private String oficina;
    private String cargo;

    public ResponsableRegistroDTO(Responsable r) {
        this.codigoFuncionario = r.getCodigoFuncionario();
        this.nombreCompleto = r.getPersona().getNombreCompleto();
        this.oficina = r.getOficina().getNombre();
        this.cargo = r.getCargo().getNombre();
    }
}