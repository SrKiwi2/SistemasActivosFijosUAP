package com.usic.SistemasActivosFijosUAP.model.entity;

import java.io.Serializable;
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

@Entity
@Table(name = "log_acceso",
        indexes = {
            @Index(name = "idx_logacceso_fecha",    columnList = "fecha_hora"),
            @Index(name = "idx_logacceso_username", columnList = "username"),
            @Index(name = "idx_logacceso_ip",       columnList = "ip"),
            @Index(name = "idx_logacceso_exito",    columnList = "exito")
        })
@Getter @Setter
public class LogAcceso implements Serializable{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idLogAcceso;

    // Quién intentó autenticarse (texto plano para no romper aunque se borre el usuario)
    @Column(length = 100, nullable = false)
    private String username;

    // Link opcional al usuario (si lo tienes)
    @Column(name = "user_id")
    private Long userId;

    // Rol al momento del login (por si cambia luego)
    @Column(length = 50)
    private String rol;

    // Resultado del intento
    @Column(nullable = false)
    private Boolean exito; // true=ok, false=fallo

    // Motivo resumido: LOGIN_OK, CREDENCIALES_INVALIDAS, USUARIO_INACTIVO, BLOQUEO_IP, etc.
    @Column(length = 32, nullable = false)
    private String motivo;

    // Datos del entorno
    @Column(length = 45)          // IPv6 máx ~45
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    // Dónde ocurrió
    @Column(length = 255)
    private String endpoint; // p.ej. "/iniciar-sesion"

    @Column(length = 10)
    private String metodo;   // POST, GET

    @Column(name = "session_id", length = 128)
    private String sessionId;

    // Cuándo (guárdalo en tu service con America/La_Paz)
    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    // Detalle extendido (stacktrace corto, mensajes, etc.)
    @Column(columnDefinition = "text")
    private String detalle;
}
