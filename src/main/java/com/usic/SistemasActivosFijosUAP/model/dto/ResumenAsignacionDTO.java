package com.usic.SistemasActivosFijosUAP.model.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

import lombok.Getter;

/**
 * Números por asignación para la vista de Movimientos → Asignaciones: cuánto suma,
 * a cuántos activos les falta el costo y cuántos llegaron ya al VSIAF.
 * <p>
 * Lo llena una única consulta agregada ({@code IAsignacionActivoDao.resumenPorAsignacion}),
 * no un recorrido de las colecciones: la pantalla lista muchas asignaciones y recorrer
 * los detalles de cada una dispararía una consulta por fila.
 */
@Getter
public class ResumenAsignacionDTO {

    private final Long idAsignacionActivo;
    private final BigDecimal costoTotal;
    /** Activos sin costo cargado: mientras haya alguno, el total es parcial. */
    private final long sinCosto;
    /** Activos que ya están en el VSIAF (estado ACTIVO). */
    private final long subidos;
    /** Activos todavía en PENDIENTE. */
    private final long pendientes;
    /**
     * Activos cuyo último envío al VSIAF fue rechazado por el worker.
     * <p>
     * Es distinto de {@link #pendientes}: estos se dieron por subidos, pero la base y el
     * VSIAF quedaron diciendo cosas distintas. Es lo único de esta fila que exige acción.
     */
    private final long conError;
    private final long total;

    /**
     * Construye el resumen desde una fila de la consulta agregada.
     * <p>
     * Se recibe {@code Number} y no tipos concretos porque cada dialecto devuelve lo
     * suyo: en PostgreSQL {@code SUM} sobre enteros da {@code numeric} → BigDecimal,
     * y {@code COUNT} da {@code bigint} → Long. Convertir acá evita un
     * ClassCastException en tiempo de ejecución.
     *
     * @param fila columnas en el orden: id, costoTotal, sinCosto, subidos, pendientes, conError, total
     */
    public static ResumenAsignacionDTO desdeFila(Object[] fila) {
        return new ResumenAsignacionDTO(
                ((Number) fila[0]).longValue(),
                aDouble(fila[1]),
                aLong(fila[2]), aLong(fila[3]), aLong(fila[4]), aLong(fila[5]), aLong(fila[6]));
    }

    private static long aLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : 0L;
    }

    private static double aDouble(Object o) {
        return (o instanceof Number n) ? n.doubleValue() : 0d;
    }

    private ResumenAsignacionDTO(Long idAsignacionActivo, double costoTotal,
                                 long sinCosto, long subidos, long pendientes,
                                 long conError, long total) {
        this.idAsignacionActivo = idAsignacionActivo;
        this.costoTotal = BigDecimal.valueOf(costoTotal).setScale(2, RoundingMode.HALF_UP);
        this.sinCosto   = sinCosto;
        this.subidos    = subidos;
        this.pendientes = pendientes;
        this.conError   = conError;
        this.total      = total;
    }

    /**
     * Etiqueta del avance hacia el VSIAF: lo que va en la columna de estado.
     * <p>
     * El orden importa: un acta con bienes rechazados es "con error" aunque además tenga
     * pendientes, porque el error es lo que exige intervención.
     */
    public String getEtiquetaSincronizacion() {
        if (conError > 0)   return "ERROR";
        if (pendientes > 0) return subidos > 0 ? "PARCIAL" : "SIN_SUBIR";
        if (subidos > 0)    return "COMPLETA";
        return "SIN_DATOS";
    }

    /** Ni subido ni pendiente: cancelados, dados de baja, etc. */
    public long getOtros() {
        return Math.max(0, total - subidos - pendientes);
    }

    public boolean isTotalParcial() {
        return sinCosto > 0;
    }
}
