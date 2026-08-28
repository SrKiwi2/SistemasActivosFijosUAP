package com.usic.SistemasActivosFijosUAP.model.dto;

/**
 * Números de las tarjetas del listado de Asignaciones.
 * <p>
 * Se calculan en el servidor sobre el conjunto <b>filtrado completo</b>, no sobre la
 * página. Antes se contaban en el navegador recorriendo las filas del DOM, lo que sin
 * paginación ya era frágil y con paginación pasaría a ser directamente falso: las
 * tarjetas mostrarían los totales de los 25 registros visibles.
 *
 * @param total     actas que devuelve el filtro
 * @param completas todos sus bienes están en el VSIAF
 * @param parciales tienen bienes subidos y bienes todavía pendientes
 * @param conError  tienen algún bien que el VSIAF rechazó: los dos sistemas difieren
 */
public record ResumenListadoAsignacionDTO(long total, long completas, long parciales, long conError) {

    public static final ResumenListadoAsignacionDTO VACIO = new ResumenListadoAsignacionDTO(0, 0, 0, 0);
}
