package com.usic.SistemasActivosFijosUAP.model.dto.control;

/**
 * Lo que viaja por SSE cuando un levantamiento se abre, avanza o se cierra.
 *
 * <p>Lleva {@code idPredio} e {@code idOficina} para que la web sepa repintar el
 * tile correcto sin volver a pedir el mapa entero: quien está mirando el nivel
 * de predios y quien está mirando una oficina reciben el mismo evento y cada uno
 * decide si le toca.
 */
public record EventoLevantamientoDTO(
        Long   idInventario,
        String numeroInventario,
        Long   idOficina,
        String oficina,
        Long   idPredio,
        int    esperados,
        int    encontrados,
        int    pendientes,
        int    faltantes,
        String estado,
        String ejecutor
) {}
