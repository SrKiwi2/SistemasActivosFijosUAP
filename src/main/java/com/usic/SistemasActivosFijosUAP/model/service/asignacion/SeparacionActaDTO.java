package com.usic.SistemasActivosFijosUAP.model.service.asignacion;

import java.util.List;

/**
 * Lo que pide una separación de acta: qué bienes salen y a qué acta nueva van.
 *
 * @param idActaOrigen         acta que se parte
 * @param idsActivos           bienes que se van a la nueva acta
 * @param idConfigGestion      tipo de documento de la nueva acta (define prefijo y gestión)
 * @param nroDocumento         número de documento de la nueva acta
 * @param idResponsableDestino responsable de la nueva acta; null hereda el del acta origen
 * @param idOficinaDestino     oficina de la nueva acta; null hereda la del acta origen
 * @param motivo               por qué se separa; obligatorio
 */
public record SeparacionActaDTO(
        Long idActaOrigen,
        List<Long> idsActivos,
        Long idConfigGestion,
        String nroDocumento,
        Long idResponsableDestino,
        Long idOficinaDestino,
        String motivo) {

    /** Mínimo de caracteres del motivo. Un "s/n" o un punto no explican nada. */
    public static final int MOTIVO_MINIMO = 10;
}
