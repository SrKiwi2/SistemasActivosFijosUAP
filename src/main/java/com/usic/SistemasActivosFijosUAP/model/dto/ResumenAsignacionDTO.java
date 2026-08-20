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
    private final long total;

    /**
     * Construye el resumen desde una fila de la consulta agregada.
     * <p>
     * Se recibe {@code Number} y no tipos concretos porque cada dialecto devuelve lo
     * suyo: en PostgreSQL {@code SUM} sobre enteros da {@code numeric} → BigDecimal,
     * y {@code COUNT} da {@code bigint} → Long. Convertir acá evita un
     * ClassCastException en tiempo de ejecución.
     *
     * @param fila columnas en el orden: id, costoTotal, sinCosto, subidos, pendientes, total
     */
    public static ResumenAsignacionDTO desdeFila(Object[] fila) {
        return new ResumenAsignacionDTO(
                ((Number) fila[0]).longValue(),
                aDouble(fila[1]),
                aLong(fila[2]), aLong(fila[3]), aLong(fila[4]), aLong(fila[5]));
    }

    private static long aLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : 0L;
    }

    private static double aDouble(Object o) {
        return (o instanceof Number n) ? n.doubleValue() : 0d;
    }

    private ResumenAsignacionDTO(Long idAsignacionActivo, double costoTotal,
                                 long sinCosto, long subidos, long pendientes, long total) {
        this.idAsignacionActivo = idAsignacionActivo;
        this.costoTotal = BigDecimal.valueOf(costoTotal).setScale(2, RoundingMode.HALF_UP);
        this.sinCosto   = sinCosto;
        this.subidos    = subidos;
        this.pendientes = pendientes;
        this.total      = total;
    }

    /** Ni subido ni pendiente: cancelados, dados de baja, etc. */
    public long getOtros() {
        return Math.max(0, total - subidos - pendientes);
    }

    public boolean isTotalParcial() {
        return sinCosto > 0;
    }
}
