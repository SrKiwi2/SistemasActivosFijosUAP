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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Notificación de activo registrado como duplicado (NOT:SCIAF:AD N° 001/2026): una por
 * registro. Dice qué activo se dispuso como duplicado, de cuál es duplicado, quién lo
 * registró y por qué, y que por eso se considera faltante y pasa a la ubicación de duplicados.
 * <p>
 * Como la notificación de faltantes, guarda una <b>foto</b> de lo emitido ({@link #contenido},
 * JSON) con su SHA-256, y el QR del papel lleva a una página pública por {@link #token}.
 * Reimprimir da el mismo documento.
 */
@Entity
@Table(name = "notificacion_duplicado",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_not_dup_numero", columnNames = "numero"),
                @UniqueConstraint(name = "uk_not_dup_token", columnNames = "token")
        },
        indexes = {
                @Index(name = "idx_not_dup_persona", columnList = "id_persona"),
                @Index(name = "idx_not_dup_fecha", columnList = "fecha_emision")
        })
@Getter
@Setter
public class NotificacionDuplicado extends AuditoriaConfig {

    private static final long serialVersionUID = 2629195288020321952L;

    public static final String VIGENTE = "VIGENTE";
    public static final String ANULADA = "ANULADA";

    /** Se guarda corto (NOT-AD-001/2026) y se imprime completo con {@link #numeroImpreso}. */
    public static final String PREFIJO = "NOT-AD-";

    public static String numeroImpreso(String numero) {
        return numero != null && numero.startsWith(PREFIJO)
                ? "NOT:SCIAF:AD N° " + numero.substring(PREFIJO.length())
                : numero;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_notificacion")
    private Long idNotificacion;

    @Column(name = "numero", length = 20, nullable = false)
    private String numero;

    /** Llave de la página de verificación (va en el QR): aleatoria, no el correlativo. */
    @Column(name = "token", length = 40, nullable = false)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", foreignKey = @ForeignKey(name = "fk_not_dup_persona"))
    private Persona persona;

    @Column(name = "persona_nombre", length = 160)
    private String personaNombre;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "usuario_emision", length = 60)
    private String usuarioEmision;

    /** Foto de lo emitido, en JSON. Es lo que muestran el PDF y la verificación. */
    @Column(name = "contenido", columnDefinition = "text", nullable = false)
    private String contenido;

    /** SHA-256 (hex) de {@link #contenido}: la verificación lo recalcula. */
    @Column(name = "hash_contenido", length = 64, nullable = false)
    private String hashContenido;

    @Column(name = "estado_notificacion", length = 15, nullable = false)
    private String estadoNotificacion;

    @Column(name = "motivo_anulacion", columnDefinition = "text")
    private String motivoAnulacion;

    @Column(name = "fecha_anulacion")
    private LocalDateTime fechaAnulacion;

    @Column(name = "usuario_anulacion", length = 60)
    private String usuarioAnulacion;
}
