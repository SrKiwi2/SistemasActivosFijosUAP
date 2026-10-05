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
        long          resueltos,
        /** 1 o 2 si es una notificación reiterativa; 0 si no. */
        int           numeroReiterativa,
        /** Faltantes que siguen apuntando a este documento (0 en una notificación ya reiterada). */
        long          vinculados,
        /** Tiene una reiterativa no anulada: ya no es la vigente y no se puede anular. */
        boolean       reiterada
) {}
