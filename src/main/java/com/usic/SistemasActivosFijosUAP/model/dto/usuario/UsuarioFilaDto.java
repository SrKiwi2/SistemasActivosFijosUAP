package com.usic.SistemasActivosFijosUAP.model.dto.usuario;

import java.time.LocalDateTime;
import java.util.Date;

import lombok.Getter;
import lombok.Setter;

/** Una fila de la pantalla de gestión de usuarios (sin la entidad: nada de lazy en la vista). */
@Getter
@Setter
public class UsuarioFilaDto {
    private Long idUsuario;
    /** Id cifrado, como lo usan los endpoints existentes del módulo. */
    private String idCifrado;
    private String usuario;
    private String nombreCompleto;
    private String ci;
    private String rol;
    private String estado;
    private boolean conectado;
    private LocalDateTime ultimoIngreso;
    private long fallidosRecientes;
    /** Cantidad de permisos asignados a mano; 0 = usa la plantilla de su rol. */
    private int permisosPropios;
    private Date registro;
    private boolean esYo;
    /** Inactivo con respaldo: cuántos permisos se restaurarían (0 = plantilla del rol; null = sin respaldo). */
    private Integer respaldo;
}
