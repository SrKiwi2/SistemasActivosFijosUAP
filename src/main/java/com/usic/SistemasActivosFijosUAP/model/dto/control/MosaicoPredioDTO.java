package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.util.List;

/**
 * Un predio del mosaico del Mapa de control, con todas sus oficinas. El mosaico completo
 * (los 14 predios y sus ~860 oficinas) viaja en una sola respuesta: entrar a un predio o
 * volver es un zoom en el navegador, sin otro pedido.
 */
public record MosaicoPredioDTO(
        Long   idPredio,
        String descrip,
        String unidad,
        Long   idMunicipio,
        String municipio,
        /** Bienes vigentes en oficinas dadas de baja del predio (no se dibujan: es una anomalía). */
        long   bienesOficinasInactivas,
        List<TileOficinaDTO> oficinas
) { }
