package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;
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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Acta de faltantes: el documento que se imprime y firma cuando se registran faltantes
 * de una persona.
 * <p>
 * Guarda una <b>foto</b> de lo que se emitió ({@link #contenido}, en JSON) y su hash
 * SHA-256. El QR del papel apunta a una página pública por {@link #token} —aleatorio, no
 * el número correlativo, para que no se puedan recorrer las actas— que muestra esa foto:
 * quien tenga el papel puede comparar. No es firma digital; lo que garantiza es que el
 * original está en el SCIAF y no se puede cambiar desde la pantalla.
 * <p>
 * Los datos de la persona se copian al emitir: si después cambia de cargo, el acta sigue
 * diciendo lo que decía el día que se firmó.
 */
@Entity
// Los nombres coinciden con scripts/sql/custodia_faltantes_fase3.sql: la tabla la crea ese
// script (es de postgres) y así hbm2ddl no intenta volver a crear nada al arrancar.
@Table(name = "acta_faltante",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_acta_falt_numero", columnNames = "numero"),
                @UniqueConstraint(name = "uk_acta_falt_token", columnNames = "token")
        },
        indexes = {
                @Index(name = "idx_acta_falt_persona", columnList = "id_persona"),
                @Index(name = "idx_acta_falt_fecha", columnList = "fecha_emision")
        })
@Getter
@Setter
public class ActaFaltante extends AuditoriaConfig {

    private static final long serialVersionUID = 2629195288020321931L;

    public static final String VIGENTE = "VIGENTE";
    public static final String ANULADA = "ANULADA";

    /** Acta de faltantes: registra faltantes nuevos y pide su traslado a la custodia. */
    public static final String TIPO_FALTANTES = "FALTANTES";
    /** Acta de regularización: faltantes que ya estaban en la oficina de faltantes del VSIAF. */
    public static final String TIPO_REGULARIZACION = "REGULARIZACION";

    /** El tipo va en el prefijo del número (AF- / AR-) y en el contenido, que cubre el hash. */
    public static String prefijo(String tipo) {
        return TIPO_REGULARIZACION.equals(tipo) ? "AR" : "AF";
    }

    public boolean esRegularizacion() {
        return numero != null && numero.startsWith("AR-");
    }

    /**
     * Desde el 2-oct-2026 los faltantes nuevos se emiten como <b>notificación</b> (formato de
     * Activos Fijos, NOT:SCIAF:AF N° 001/2026) en vez de acta. El número se guarda corto
     * (NOT-AF-001/2026: la columna es de 20) y se imprime completo con {@link #numeroImpreso}.
     */
    public static final String PREFIJO_NOTIFICACION = "NOT-AF-";

    public static String numeroImpreso(String numero) {
        return numero != null && numero.startsWith(PREFIJO_NOTIFICACION)
                ? "NOT:SCIAF:AF N° " + numero.substring(PREFIJO_NOTIFICACION.length())
                : numero;
    }

    /**
     * Tipos de notificación en la cadena de reiterativas.
     */
    public enum TipoNotificacion {
        INICIAL,           // Primera notificación de faltantes
        REITERATIVA_1,     // Primera reiterativa (2da notificación)
        REITERATIVA_2,     // Segunda reiterativa / Última notificación (3ra notificación)
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_acta")
    private Long idActa;

    /** AF-2026-000014. Se arma con el id, así que se completa después del primer INSERT. */
    @Column(name = "numero", length = 20)
    private String numero;

    /** Llave de la página de verificación (va en el QR). */
    @Column(name = "token", length = 40, nullable = false)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", nullable = false, foreignKey = @ForeignKey(name = "fk_acta_falt_persona"))
    private Persona persona;

    @Column(name = "persona_nombre", length = 160, nullable = false)
    private String personaNombre;

    @Column(name = "persona_ci", length = 20)
    private String personaCi;

    @Column(name = "persona_cargo", length = 120)
    private String personaCargo;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "usuario_emision", length = 60)
    private String usuarioEmision;

    @Column(name = "documento_respaldo", length = 120)
    private String documentoRespaldo;

    @Column(name = "fecha_documento")
    private LocalDate fechaDocumento;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    @Column(name = "total_bienes", nullable = false)
    private Integer totalBienes;

    /** Foto de lo emitido: persona y bienes agrupados, en JSON. Es lo que muestra la verificación. */
    @Column(name = "contenido", columnDefinition = "text", nullable = false)
    private String contenido;

    /** SHA-256 (hex) de {@link #contenido}: la verificación lo recalcula para detectar cambios. */
    @Column(name = "hash_contenido", length = 64, nullable = false)
    private String hashContenido;

    /** VIGENTE | ANULADA. "Resuelta" no se guarda: sale de sus faltantes. */
    @Column(name = "estado_acta", length = 15, nullable = false)
    private String estadoActa;

    @Column(name = "motivo_anulacion", columnDefinition = "text")
    private String motivoAnulacion;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "usuario_anulacion", length = 60)
    private String usuarioAnulacion;

    // ── Reiterativas ───────────────────────────────────────────────────────────
    
    /** Tipo de notificación: INICIAL, REITERATIVA_1, REITERATIVA_2. */
    @Column(name = "tipo_notificacion", length = 20)
    private String tipoNotificacion;

    /** Acta/notificación anterior en la cadena de reiterativas (null si es la inicial). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_acta_anterior", foreignKey = @ForeignKey(name = "fk_acta_falt_anterior"))
    private ActaFaltante actaAnterior;

    /** Número de reiterativa: 0=inicial, 1=primera reiterativa, 2=segunda/última. */
    @Column(name = "numero_reiterativa")
    private Integer numeroReiterativa;
}
