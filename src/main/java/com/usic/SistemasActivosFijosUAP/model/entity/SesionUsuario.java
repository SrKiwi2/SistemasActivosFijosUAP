package com.usic.SistemasActivosFijosUAP.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

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
import lombok.Getter;
import lombok.Setter;

/**
 * Una sesión abierta en un equipo: cada inicio de sesión crea una. Es lo que el usuario ve
 * en "Mis sesiones abiertas" y lo que el administrador puede cerrar.
 *
 * <p>Si se marcó "Mantener la sesión iniciada en este equipo", el navegador guarda una
 * cookie {@code serie:token} y la sesión sobrevive a cerrar el navegador y a los
 * reinicios del servidor (ver RecordarmeService). Del token solo se guarda el hash, y la
 * IP y el navegador se actualizan con el uso: si alguien copiara la cookie, se vería ahí.
 */
@Entity
@Table(name = "sesion_usuario",
        indexes = {
            @Index(name = "idx_sesusr_usuario", columnList = "id_usuario"),
            @Index(name = "idx_sesusr_estado",  columnList = "estado"),
            @Index(name = "idx_sesusr_serie",   columnList = "serie", unique = true)
        })
@Getter @Setter
public class SesionUsuario implements Serializable {

    public static final String ACTIVA = "ACTIVA";
    public static final String CERRADA = "CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idSesionUsuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false,
                foreignKey = @ForeignKey(name = "fk_sesusr_usuario"))
    private Usuario usuario;

    /** Identificador público de la sesión dentro de la cookie de "recordarme". */
    @Column(name = "serie", length = 40, nullable = false)
    private String serie;

    /** SHA-256 del token (solo sesiones recordadas). */
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "recordar", nullable = false, columnDefinition = "boolean not null default false")
    private boolean recordar;

    /** Identificador del navegador (cookie SCIAF_EQUIPO): sirve para avisar de equipos nuevos. */
    @Column(name = "equipo", length = 40)
    private String equipo;

    /** "Chrome en Windows", para mostrar. */
    @Column(name = "dispositivo", length = 120)
    private String dispositivo;

    /** IP del último uso (la del ingreso queda en log_acceso). */
    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    /** JSESSIONID vigente de este equipo (cambia cada vez que se restaura la sesión). */
    @Column(name = "id_sesion_http", length = 64)
    private String idSesionHttp;

    @Column(name = "creado", nullable = false)
    private LocalDateTime creado;

    @Column(name = "ultimo_uso", nullable = false)
    private LocalDateTime ultimoUso;

    /** Solo recordadas: después de esta fecha pide la contraseña otra vez. */
    @Column(name = "expira")
    private LocalDateTime expira;

    @Column(name = "estado", length = 12, nullable = false)
    private String estado = ACTIVA;

    @Column(name = "cerrada_en")
    private LocalDateTime cerradaEn;

    /** LOGOUT, INACTIVIDAD, CERRADA_POR_USUARIO, CERRADA_POR_ADMIN, CIERRE_FORZADO, EXPIRADA... */
    @Column(name = "motivo_cierre", length = 40)
    private String motivoCierre;

    /** Usuario que la cerró, si no fue el mismo dueño. */
    @Column(name = "cerrada_por")
    private Long cerradaPor;
}
