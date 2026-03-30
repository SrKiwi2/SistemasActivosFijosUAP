package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import lombok.Data;

@Data
public class DetalleRegistroItem {
    private Long idMunicipio;
    private Long idPredio;
    private Long idOficina;
    private Long idResponsable;
    private Long idGrupoContable;
    private Long idAuxiliar;
    private String descripcion;
    private Double costo;
    private Integer vidaUtil;
    private Integer cantidad;

    private List<DetalleActivoDTO> detalles;
}
