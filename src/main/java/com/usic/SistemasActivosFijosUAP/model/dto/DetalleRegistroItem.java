package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Data;

@Data
public class DetalleRegistroItem {
    private Long idMunicipio;
    private Long idPredio;
    private Long idOficina;
    private Long idGrupoContable;
    private Long idAuxiliar;
    private String descripcion;
    private Double costo; // Opcional
    private Integer vidaUtil;
    private Integer cantidad; // Cuántos iguales de este tipo crear
}
