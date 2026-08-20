package com.usic.SistemasActivosFijosUAP.model.dto.movil;

import java.util.List;

/**
 * Veredicto de un escaneo: qué se leyó, qué dice el sistema y en qué difieren.
 *
 * <p>La app muestra {@code activo} como dato principal y {@code discrepancias}
 * como advertencia. La etiqueta nunca gana: es papel impreso en el pasado.
 */
public record EscaneoResultadoDTO(

        /** Código ya normalizado, sin prefijo de entidad. */
        String codigoDetectado,
        /** Código tal como estaba impreso, con prefijo. */
        String codigoVisual,
        String prefijoEntidad,
        /** false = el prefijo no corresponde a la entidad de este sistema. */
        boolean entidadValida,

        /**
         * OK · ETIQUETA_DESACTUALIZADA · REUBICADO · NO_ENCONTRADO ·
         * OTRA_ENTIDAD · ILEGIBLE · VARIOS_CANDIDATOS
         */
        String veredicto,
        /** Frase lista para mostrar, ya resuelta en el servidor. */
        String mensaje,

        ActivoFichaMovilDTO activo,
        List<DiscrepanciaDTO> discrepancias,

        /**
         * Coincidencias cuando se tecleó solo el correlativo y hay más de un
         * activo posible: el usuario elige.
         */
        List<ActivoFichaMovilDTO> candidatos
) {}
