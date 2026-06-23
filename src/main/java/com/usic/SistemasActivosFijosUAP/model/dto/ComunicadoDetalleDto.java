package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Detalle de un comunicado para el panel de control del emisor: qué se mandó,
 * a quién, quién recibió y quién confirmó la lectura.
 */
@Getter @Builder
public class ComunicadoDetalleDto {

    private Long    idComunicado;
    private String  tipo;
    private String  titulo;
    private String  mensaje;
    private String  urlDestino;
    private boolean importante;
    private String  alcance;
    private String  alcanceDetalle;
    private String  emisor;
    private String  fechaEnvio;

    private int  totalDestinatarios;
    private long recibieron;        // entregadas
    private long leyeron;           // confirmadas
    private long pendientes;        // ni siquiera entregadas
    private int  porcentajeLectura; // 0-100

    private List<Destinatario> destinatarios;

    @Getter @Builder
    public static class Destinatario {
        private String  nombre;
        private String  usuario;
        private boolean entregada;
        private String  fechaEntrega;
        private boolean leida;
        private String  fechaLectura;
    }
}
