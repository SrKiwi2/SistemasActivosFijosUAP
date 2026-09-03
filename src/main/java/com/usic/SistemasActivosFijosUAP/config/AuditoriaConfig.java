package com.usic.SistemasActivosFijosUAP.config;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trae los 5 campos de auditoría que comparten las ~40 entidades del sistema que
 * extienden esta clase: cuándo y quién registró, cuándo y quién modificó por última
 * vez, y el estado.
 * <p>
 * Desde que {@code JpaAuditingConfig} habilita {@code @EnableJpaAuditing} con
 * {@link UsuarioAuditorAware} como auditor, {@code @CreatedDate}/{@code @CreatedBy}/
 * {@code @LastModifiedDate}/{@code @LastModifiedBy} los procesa
 * {@link AuditingEntityListener} solos en cada INSERT/UPDATE — ya no hace falta
 * escribirlos a mano. Código anterior a esto (por ejemplo
 * {@code AsignacionEdicionService}) que sigue seteándolos explícitamente no rompe
 * nada: simplemente queda redundante con lo que ahora hace el listener solo.
 * <p>
 * Verificado antes de activarlo que nada de la interoperabilidad con el VSIAF lee
 * estos campos — ver el javadoc de {@code JpaAuditingConfig}.
 * <p>
 * {@link #estado} quedó fuera a propósito: tenía {@code @CreatedBy} por error (un
 * campo de texto no es lo que audita un "quién"), y con el auditor activo eso habría
 * reventado cualquier guardado del sistema — el auditor devuelve un id de usuario
 * (Long), no compatible con un campo String.
 * <p>
 * {@link #alModificar()} queda como respaldo manual de {@code modificacion} — se
 * solapa con {@code @LastModifiedDate} (los dos sellan lo mismo en cada UPDATE, sin
 * conflicto) hasta confirmar en producción que la auditoría automática realmente está
 * funcionando; después se puede quitar.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
@Setter
@Getter
@NoArgsConstructor
public abstract class AuditoriaConfig implements Serializable{
    private static final long serialVersionUID = 2629195288020321924L;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "_fecha_registro")
    @CreatedDate
    private Date registro = new Timestamp(System.currentTimeMillis());

    @CreatedBy
    @Column(name = "_registro_idUsuario")
    private Long registroIdUsuario;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "_fecha_modificacion")
    @LastModifiedDate
    private Date modificacion = new Timestamp(System.currentTimeMillis());

    @LastModifiedBy
    @Column(name = "_modificacion_idUsuario")
    private Long modificacionIdUsuario;

    @Column(name = "_estado")
    private String estado;

    @PreUpdate
    protected void alModificar() {
        this.modificacion = new Timestamp(System.currentTimeMillis());
    }
}
