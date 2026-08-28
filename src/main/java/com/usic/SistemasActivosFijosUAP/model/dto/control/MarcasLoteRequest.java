package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * Lote de marcas que la app envía al recuperar conexión.
 *
 * <p>Reenviarlo es seguro: cada marca se aplica por {@code idDetalle} y se
 * ignora si el servidor ya tiene una más nueva. Así la cola offline puede
 * reintentar sin miedo a duplicar ni a retroceder el trabajo.
 */
public record MarcasLoteRequest(
        String uuidCliente,
        @NotEmpty(message = "El lote no puede venir vacío")
        List<MarcaRequest> marcas
) {}
