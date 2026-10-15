package com.usic.SistemasActivosFijosUAP.model.dto.adicional;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

/**
 * Alta (o corrección) de activos adicionales. Nada es obligatorio salvo tener algo con qué
 * reconocer el bien: la descripción o el código antiguo.
 */
@Data
public class AdicionalRequest {

    /** Marca del intento de alta (evita duplicar si la respuesta se pierde). */
    private String token;

    // ── Identificación ──
    private String descripcion;
    /** Un código antiguo por unidad, en orden; vacío = esa unidad no tiene. */
    private List<String> codigosAntiguos;
    private Integer cantidad;

    // ── Clasificación y ubicación (opcionales) ──
    private Long idPredio;
    private Long idOficina;
    private Long idResponsable;
    private Long idGrupoContable;
    private Long idAuxiliar;
    private Long idEstadoActivo;
    private Long idOrganismoFinanciero;

    // ── Datos ──
    private LocalDate fechaAdquisicion;
    private Double valorUnitario;
    private Integer vidaUtil;
    private String marca;
    private String modelo;
    private String color;
    private String material;
    private String dimensiones;
    /** Una serie por unidad, en orden; vacía = esa unidad sin serie. */
    private List<String> series;
    private String caracteristicas;
    private String observacion;
}
