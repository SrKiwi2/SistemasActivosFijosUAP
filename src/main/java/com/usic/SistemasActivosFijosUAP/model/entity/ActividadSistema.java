package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Una acción de un usuario en el sistema (registro, modificación, eliminación, solicitud
 * y resolución de autorizaciones, bloqueos…), para el módulo de Monitoreo de actividad.
 * <p>
 * Es solo bitácora: nada del sistema la lee para decidir. Se escribe en su propia
 * transacción y nunca frena la operación que registra.
 */
@Entity
@Table(name = "actividad_sistema", indexes = {
    @Index(name = "ix_actividad_fecha", columnList = "fecha"),
    @Index(name = "ix_actividad_usuario", columnList = "id_usuario"),
    @Index(name = "ix_actividad_modulo", columnList = "modulo, accion")
})
@Getter @Setter
public class ActividadSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_actividad")
    private Long idActividad;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "rol", length = 40)
    private String rol;

    /** OFICINA, RESPONSABLE, ACTIVO, ASIGNACION, TRANSFERENCIA, BLOQUEO, AUTORIZACION… */
    @Column(name = "modulo", length = 30, nullable = false)
    private String modulo;

    /** REGISTRO, MODIFICACION, ELIMINACION, SOLICITUD, APROBACION, RECHAZO… */
    @Column(name = "accion", length = 30, nullable = false)
    private String accion;

    /** Código visible para buscar el registro en su módulo (código de activo, de oficina…). */
    @Column(name = "referencia", length = 200)
    private String referencia;

    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;

    /** Cantidad de registros que abarca (p. ej. un lote de activos). */
    @Column(name = "cantidad")
    private Integer cantidad;

    /** Id del registro en el SCIAF, cuando es uno solo. */
    @Column(name = "id_registro")
    private Long idRegistro;
}
