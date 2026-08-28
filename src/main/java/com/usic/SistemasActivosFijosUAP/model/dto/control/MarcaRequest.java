package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDateTime;

/**
 * Una marca hecha en campo sobre un activo.
 *
 * <p>Se identifica por {@code idDetalle} cuando el activo estaba en la lista
 * esperada, o por {@code codigo} cuando apareció algo que no se esperaba en esa
 * oficina — ese caso se registra como <b>SOBRANTE</b> y no se descarta: un
 * activo que está donde no debería es tan hallazgo como uno que falta.
 */
public record MarcaRequest(
        Long          idDetalle,
        String        codigo,
        /** ENCONTRADO | FALTANTE | PENDIENTE (para deshacer una marca). */
        String        situacion,
        /** ESCANEO | MANUAL | WEB */
        String        origen,
        /**
         * Hora del dispositivo, no del servidor: en campo se trabaja sin señal y
         * el lote puede llegar horas después. Es además el criterio para no pisar
         * una marca más nueva cuando la app reenvía la cola.
         */
        LocalDateTime fecha,
        String        observacion,
        Long          idEstadoObservado
) {}
