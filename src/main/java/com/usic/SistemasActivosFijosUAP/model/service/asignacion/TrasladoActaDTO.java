package com.usic.SistemasActivosFijosUAP.model.service.asignacion;

import java.util.List;

/**
 * Mueve bienes hacia un acta que ya existe.
 * <p>
 * Cubre las dos operaciones que en la pantalla se ven distintas pero son la misma: sacar
 * bienes de un acta hacia otra ("trasladar") y sumarle bienes a un acta desde donde estén
 * ("incorporar"). El origen no se pide: cada bien está en una sola acta vigente y el
 * sistema ya sabe cuál.
 * <p>
 * No existe una operación de "quitar". Un bien siempre queda en un acta: si se lo saca de
 * una, es porque entra en otra. Así nunca hay bienes huérfanos y la pregunta "¿dónde está
 * este activo?" tiene siempre respuesta.
 *
 * @param idActaDestino   acta que recibe los bienes
 * @param idsActivos      bienes a mover
 * @param adoptarDestino  si los bienes toman el responsable y la oficina del acta destino.
 *                        Va desmarcado por defecto: propagar en silencio dispararía
 *                        escrituras al VSIAF que nadie pidió, y no propagar nunca dejaría
 *                        la cabecera diciendo una cosa y los bienes otra
 * @param motivo          por qué se mueven; obligatorio
 */
public record TrasladoActaDTO(
        Long idActaDestino,
        List<Long> idsActivos,
        boolean adoptarDestino,
        String motivo) {
}
