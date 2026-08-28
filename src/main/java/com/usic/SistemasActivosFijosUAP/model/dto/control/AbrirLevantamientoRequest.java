package com.usic.SistemasActivosFijosUAP.model.dto.control;

import jakarta.validation.constraints.NotNull;

/**
 * Apertura de un levantamiento.
 *
 * <p>{@code uuidCliente} lo genera el teléfono antes de llamar. Si la respuesta
 * se pierde y la app reintenta, se devuelve el mismo levantamiento en vez de
 * abrir otro sobre la misma oficina.
 */
public record AbrirLevantamientoRequest(
        @NotNull(message = "Indique la oficina a levantar")
        Long   idOficina,
        String uuidCliente,
        String descripcion
) {}
