package com.usic.SistemasActivosFijosUAP.model.dto.movil;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ficha del activo tal como la muestra la app: plana, con los nombres ya
 * resueltos y sin relaciones perezosas.
 *
 * <p>Se devuelve como {@code record} y no como la entidad para no arrastrar el
 * grafo JPA entero por la red — con conexiones lentas, cada campo de más se
 * paga.
 */
public record ActivoFichaMovilDTO(

        Long   idActivo,
        /** Código en BD: {@code 01-04-02-03609}. */
        String codigo,
        /** Código como está impreso en la etiqueta: {@code 148-01-04-02-03609}. */
        String codigoVisual,
        String descripcion,

        /** ACTIVO · PENDIENTE · CANCELADO · BAJA · ELIMINADO */
        String estado,
        /** Estado físico (BUENO, REGULAR…), distinto del estado de registro. */
        String estadoFisico,

        Double     costo,
        Double     depreciacionAcum,
        BigDecimal vidaUtil,
        LocalDate  fechaAdquisicion,
        String     observaciones,

        Referencia grupoContable,
        Referencia auxiliar,
        Referencia organismoFinanciero,

        Ubicacion   ubicacion,
        Responsable responsable,

        /** Última modificación conocida en el VSIAF. */
        LocalDate fechaUltimaModificacion,
        String    usuarioUltimaModificacion
) {

    /** Par código + nombre, que es todo lo que la app necesita de un catálogo. */
    public record Referencia(String codigo, String nombre) {}

    public record Ubicacion(
            Long   idOficina,
            String oficina,
            String predio,
            String predioCodigo,
            String unidad,
            String ciudad,
            String municipio,
            String municipioCodigo,
            String entidad,
            String entidadSigla,
            String entidadCodigo
    ) {}

    public record Responsable(
            Long   idResponsable,
            String nombre,
            String cargo,
            String ci
    ) {}
}
