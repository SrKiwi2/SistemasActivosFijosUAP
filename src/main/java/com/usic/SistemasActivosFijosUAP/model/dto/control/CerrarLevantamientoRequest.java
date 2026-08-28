package com.usic.SistemasActivosFijosUAP.model.dto.control;

/** Cierre del levantamiento: lo que quedó pendiente pasa a faltante. */
public record CerrarLevantamientoRequest(
        String uuidCliente,
        String observ
) {}
