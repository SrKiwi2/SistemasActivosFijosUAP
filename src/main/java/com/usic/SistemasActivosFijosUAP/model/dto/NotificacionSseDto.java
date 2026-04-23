package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class NotificacionSseDto {
    
    private Long   idNotificacion;
    private String tipo;
    private String titulo;
    private String mensaje;
    private String referenciaId;
    private String urlDestino;
    private String fechaCreacion;  // formateado para mostrar
    private long   noLeidasTotal;  // conteo actualizado del usuario
}
