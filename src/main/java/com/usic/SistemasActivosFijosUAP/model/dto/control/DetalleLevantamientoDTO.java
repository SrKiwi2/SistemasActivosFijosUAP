package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDateTime;

/**
 * Una línea de la lista esperada, tal como la ve la web y la app.
 *
 * <p>Los datos del activo son los que se congelaron al abrir el levantamiento,
 * no los actuales: el acta tiene que seguir diciendo lo que decía ese día.
 */
public record DetalleLevantamientoDTO(
        Long          idDetalle,
        Long          idActivo,
        String        codigo,
        String        descripcion,
        Long          idResponsable,
        String        responsable,
        /** PENDIENTE | ENCONTRADO | FALTANTE */
        String        situacion,
        /** ESCANEO | MANUAL | WEB */
        String        origenMarca,
        LocalDateTime fechaMarca,
        String        observacion,
        Long          idEstadoObservado,
        String        estadoObservado
) {}
