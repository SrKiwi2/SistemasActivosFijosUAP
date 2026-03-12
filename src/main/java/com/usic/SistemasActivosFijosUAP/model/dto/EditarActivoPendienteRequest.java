package com.usic.SistemasActivosFijosUAP.model.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EditarActivoPendienteRequest {
    private String      descripcion;
    private Double      costo;
    private Double      vidaUtil;
    private LocalDate   fechaAdquisicion;
    private String      observ;

    private Long idGrupoContable;
    private Long idAuxiliar;           // null = limpiar
    private Long idOficina;
    private Long idResponsable;
    private Long idOrganismoFinanciero; // null = limpiar

    private String idEnc;
}
