package com.usic.SistemasActivosFijosUAP.model.service.control;

/**
 * Condición de negocio esperable, no un fallo: cerrar un levantamiento que ya
 * estaba cerrado, resolver dos veces el mismo hallazgo, apuntar a una oficina
 * que no existe.
 *
 * <p>Existe para separarla de {@code IllegalArgumentException} y
 * {@code IllegalStateException}, que en el resto del sistema señalan errores de
 * programación. Sin esa distinción, el manejador móvil tendría que capturar
 * ambas de forma genérica y terminaría devolviendo mensajes internos de
 * cualquier controlador como si fueran texto para el usuario.
 *
 * <p>El mensaje está escrito para que se muestre tal cual en pantalla: quien lo
 * lee está parado en un pasillo con el teléfono en la mano y necesita saber qué
 * hacer, no que algo falló.
 */
public class ReglaNegocioException extends RuntimeException {

    private static final long serialVersionUID = 2629195288020321927L;

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
