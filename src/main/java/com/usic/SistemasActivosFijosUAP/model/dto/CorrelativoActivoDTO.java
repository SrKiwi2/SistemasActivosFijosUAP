package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * Proyección ligera para la pantalla de revisión de correlativos
 * (Reportes y Consultas → Conciliación VSIAF → Revisión de correlativos).
 *
 * Trae sólo lo necesario para listar, por una combinación
 * municipio+predio+grupo contable, los activos ya codificados y poder
 * compararlos correlativamente con el VSIAF.
 *
 * El orden y el tipo de los parámetros del constructor deben coincidir con la
 * expresión {@code new ...CorrelativoActivoDTO(...)} de la consulta JPQL en
 * {@code IActivoDao}.
 */
@Getter
@Setter
public class CorrelativoActivoDTO {

    private Long    idActivo;
    private String  codigo;
    private String  descripcion;
    private String  oficinaNombre;
    private Short   codOfi;
    private String  estado;

    public CorrelativoActivoDTO(Long idActivo, String codigo, String descripcion,
                                String oficinaNombre, Short codOfi, String estado) {
        this.idActivo      = idActivo;
        this.codigo        = codigo;
        this.descripcion   = descripcion;
        this.oficinaNombre = oficinaNombre;
        this.codOfi        = codOfi;
        this.estado        = estado;
    }
}
