package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDateTime;
import java.util.List;

/**
 * El levantamiento completo. En la app es además el <b>paquete offline</b>: se
 * baja una vez al abrir y alcanza para recorrer la oficina entera sin señal.
 *
 * <p>Acotado a una oficina son cientos de activos, no los más de 30.000 del
 * maestro — por eso este es el único caso donde sí se descarga la lista por
 * adelantado.
 */
public record LevantamientoDTO(
        Long          idInventario,
        String        numeroInventario,
        Long          idOficina,
        Short         codOfi,
        String        oficina,
        Long          idPredio,
        String        predio,
        /** EN_EJECUCION | COMPLETADO */
        String        estado,
        /** WEB | MOVIL */
        String        origen,
        LocalDateTime fechaInicio,
        LocalDateTime fechaFin,
        String        ejecutor,
        int           totalEsperados,
        int           totalEncontrados,
        int           totalPendientes,
        int           totalFaltantes,
        String        observ,
        List<DetalleLevantamientoDTO> detalle
) {}
