package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.ForeignKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "notificacion",
    indexes = {
        @Index(name = "idx_notif_usuario",   columnList = "id_usuario"),
        @Index(name = "idx_notif_leida",     columnList = "leida"),
        @Index(name = "idx_notif_tipo",      columnList = "tipo"),
        @Index(name = "idx_notif_fecha",     columnList = "fecha_creacion"),
        @Index(name = "idx_notif_referencia",columnList = "referencia_id")
    }
)
@Getter @Setter
public class Notificacion extends AuditoriaConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idNotificacion;

    // Usuario destinatario
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false,
                foreignKey = @ForeignKey(name = "fk_notif_usuario"))
    private Usuario usuario;

    // Tipo de notificación — extensible a futuro
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 50)
    private TipoNotificacion tipo;

    // Título corto (ej: "Nueva transferencia pendiente")
    @Size(max = 255)
    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    // Mensaje descriptivo (ej: "Correlativo REC:AF:TRF:N-012/2026 — 3 activos")
    @Column(name = "mensaje", columnDefinition = "text")
    private String mensaje;

    // ID de referencia al objeto relacionado (ej: corrT de la transferencia)
    @Size(max = 120)
    @Column(name = "referencia_id", length = 120)
    private String referenciaId;

    // URL a la que redirige al hacer click
    @Size(max = 512)
    @Column(name = "url_destino", length = 512)
    private String urlDestino;

    @Column(name = "leida", nullable = false)
    private boolean leida = false;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_lectura")
    private LocalDateTime fechaLectura;

    // Enum de tipos
    public enum TipoNotificacion {
        TRANSFERENCIA_NUEVA,
        TRANSFERENCIA_APROBADA,
        TRANSFERENCIA_ERROR,
        SYNC_COMPLETADO,
        SISTEMA
    }
}
