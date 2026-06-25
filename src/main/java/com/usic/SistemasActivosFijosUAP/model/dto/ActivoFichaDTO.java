package com.usic.SistemasActivosFijosUAP.model.dto;

/**
 * Respuesta de la API pública de consulta de un activo por su código.
 *
 * Devuelve, dado un código (ej. "01-01-08-00583"), los datos básicos de
 * ubicación del activo: responsable, oficina, unidad (predio) y descripción.
 *
 * Los campos pueden venir en {@code null} si el activo no tiene esa relación
 * asignada (p. ej. un activo sin responsable).
 */
public record ActivoFichaDTO(
        String codigo,
        String responsable,
        String oficina,
        String unidad,
        String descripcion
) {
}
