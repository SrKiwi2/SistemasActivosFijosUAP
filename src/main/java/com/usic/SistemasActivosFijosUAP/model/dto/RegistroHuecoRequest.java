package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Data;

/**
 * Petición para registrar un activo en un CÓDIGO PUNTUAL (un "hueco" de la serie),
 * desde el módulo de revisión de correlativos. El código viene fijo; el backend
 * valida que el prefijo corresponda al predio/grupo de la oficina elegida y que el
 * código esté libre (el índice único uk_activo_codigo es la red final).
 */
@Data
public class RegistroHuecoRequest {
    private String  codigo;                 // código fijo (mun-pred-grupo-correlativo)
    private Long    idOficina;              // obligatorio
    private Long    idResponsable;          // obligatorio
    private Long    idGrupoContable;        // obligatorio (debe coincidir con el grupo del código)
    private Long    idAuxiliar;             // opcional
    private Long    idOrganismoFinanciero;  // opcional
    private String  descripcion;            // obligatorio
    private String  fechaAdquisicion;       // opcional (ISO yyyy-MM-dd)
    private Double  costo;                  // opcional
    private Integer vidaUtil;               // opcional
}
