package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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

/**
 * Dispositivo Android/iOS con sesión abierta en la app móvil (SCIAF Móvil).
 *
 * <p>Guarda el <b>refresh token</b> de cada dispositivo. El access token (JWT) es
 * de vida corta y no se persiste; el refresh token sí, y por eso la sesión puede
 * mantenerse indefinidamente ("logueado hasta cerrar sesión") y a la vez ser
 * revocable: basta con poner {@code activo = false} para expulsar al dispositivo.
 *
 * <p>Un mismo usuario puede tener varios dispositivos. La pareja
 * (usuario, deviceId) es la que se reutiliza en cada login para no acumular
 * filas basura.
 */
@Entity
@Table(
    name = "dispositivo_movil",
    indexes = {
        @Index(name = "idx_dispmovil_usuario",  columnList = "id_usuario"),
        @Index(name = "idx_dispmovil_device",   columnList = "device_id"),
        @Index(name = "idx_dispmovil_refresh",  columnList = "refresh_token")
    }
)
@Getter
@Setter
public class DispositivoMovil extends AuditoriaConfig {

    private static final long serialVersionUID = 7431905288020321931L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDispositivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false,
                foreignKey = @ForeignKey(name = "fk_dispmovil_usuario"))
    private Usuario usuario;

    /** Identificador estable que genera la app en la primera ejecución. */
    @Size(max = 120)
    @Column(name = "device_id", length = 120)
    private String deviceId;

    @Size(max = 30)
    @Column(name = "plataforma", length = 30)
    private String plataforma;      // android | ios | web

    @Size(max = 120)
    @Column(name = "modelo", length = 120)
    private String modelo;

    @Size(max = 30)
    @Column(name = "app_version", length = 30)
    private String appVersion;

    /**
     * Refresh token opaco (UUID). Se rota en cada renovación: el token usado
     * queda inservible en el mismo instante en que se entrega el nuevo.
     */
    @Size(max = 100)
    @Column(name = "refresh_token", length = 100)
    private String refreshToken;

    /** Token de Firebase Cloud Messaging para notificaciones push (Fase 8). */
    @Size(max = 512)
    @Column(name = "token_fcm", length = 512)
    private String tokenFcm;

    @Column(name = "ultimo_acceso")
    private LocalDateTime ultimoAcceso;

    @Column(name = "fecha_alta")
    private LocalDateTime fechaAlta;

    /**
     * false = sesión revocada. El refresh deja de funcionar y el dispositivo
     * queda fuera en cuanto caduque su access token.
     */
    @Column(name = "activo", nullable = false,
            columnDefinition = "boolean not null default true")
    private boolean activo = true;
}
