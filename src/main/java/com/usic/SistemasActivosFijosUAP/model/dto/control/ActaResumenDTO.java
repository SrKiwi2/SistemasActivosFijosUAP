package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDateTime;

/** Fila de la lista de actas de faltantes, con el avance del traslado a la custodia. */
public record ActaResumenDTO(
        Long          idActa,
        String        numero,
        LocalDateTime fechaEmision,
        String        usuarioEmision,
        Long          idPersona,
        String        personaNombre,
        String        personaCi,
        int           totalBienes,
        /** VIGENTE | ANULADA (RESUELTA se calcula en pantalla con {@link #resueltos}). */
        String        estadoActa,
        String        documentoRespaldo,
        long          esperando,
        long          enviados,
        long          enCustodia,
        long          conError,
        long          resueltos
) {}
