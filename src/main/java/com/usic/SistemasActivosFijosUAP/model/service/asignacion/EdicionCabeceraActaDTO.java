package com.usic.SistemasActivosFijosUAP.model.service.asignacion;

/**
 * Cambia el responsable y/o la oficina de la cabecera de un acta ya emitida.
 * <p>
 * A diferencia de {@link SeparacionActaDTO} y {@link TrasladoActaDTO}, acá no hay un
 * "acta original" de la que heredar: un id nulo significa que ese dato de la cabecera se
 * queda como estaba, no que se copie de otro lado.
 *
 * @param idActa                acta a editar
 * @param idResponsableDestino  nuevo responsable; null = no cambia
 * @param idOficinaDestino      nueva oficina; null = no cambia
 * @param propagarABienes       si el cambio también se aplica a los bienes vigentes del
 *                              acta. Va desmarcado por defecto: propagar en silencio
 *                              dispararía escrituras al VSIAF que nadie pidió, y no
 *                              propagar nunca dejaría la cabecera diciendo una cosa y los
 *                              bienes otra
 * @param motivo                por qué se edita; obligatorio
 */
public record EdicionCabeceraActaDTO(
        Long idActa,
        Long idResponsableDestino,
        Long idOficinaDestino,
        boolean propagarABienes,
        String motivo) {
}
