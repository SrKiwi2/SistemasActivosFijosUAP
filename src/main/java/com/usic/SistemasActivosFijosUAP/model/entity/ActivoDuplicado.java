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
import lombok.Getter;
import lombok.Setter;

/**
 * Un activo registrado como <b>duplicado</b>: el mismo bien físico está dos veces en el VSIAF
 * con dos códigos (error del revalúo). {@link #activo} es el registro que sobra y va a la
 * oficina de duplicados de su predio; {@link #activoPrincipal} es el que se queda con quien
 * tiene el bien. No se corrige nada: lo regulariza el nuevo revalúo.
 * <p>
 * Ciclo ({@link #estadoDuplicado}): {@code EN_PROCESO} (registrado, traslado en curso) →
 * {@code EN_DUPLICADOS} (el VSIAF confirmó el traslado) → {@code RETIRADO} (se sacó con
 * justificación). {@code ANULADO}: se registró por error antes de que el bien se moviera.
 * <p>
 * El traslado va en pasos, como en faltantes ({@link #estadoEnvio}): {@code ESPERANDO_ALTA}
 * (se espera que el VSIAF confirme la oficina y el responsable de duplicados), {@code ENVIADO}
 * (el bien se movió en el SCIAF y su UPDATE de ACTUAL está en la cola), {@code CONFIRMADO} y
 * {@code ERROR}.
 */
@Entity
// Los nombres coinciden con scripts/sql/activos_duplicados_fase1.sql (que además crea el
// índice parcial uk_dup_activo_pendiente: un duplicado pendiente por activo).
@Table(name = "activo_duplicado",
        indexes = {
                @Index(name = "idx_dup_activo", columnList = "id_activo"),
                @Index(name = "idx_dup_principal", columnList = "id_activo_principal"),
                @Index(name = "idx_dup_estado", columnList = "estado_duplicado"),
                @Index(name = "idx_dup_envio", columnList = "estado_envio")
        })
@Getter
@Setter
public class ActivoDuplicado extends AuditoriaConfig {

    private static final long serialVersionUID = 2629195288020321951L;

    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String EN_DUPLICADOS = "EN_DUPLICADOS";
    public static final String RETIRADO = "RETIRADO";
    public static final String ANULADO = "ANULADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_duplicado")
    private Long idDuplicado;

    /** El registro que sobra: va a la oficina de duplicados. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo", nullable = false, foreignKey = @ForeignKey(name = "fk_dup_activo"))
    private Activo activo;

    /** El registro que se queda con quien tiene el bien. No se toca. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo_principal", nullable = false, foreignKey = @ForeignKey(name = "fk_dup_principal"))
    private Activo activoPrincipal;

    /** Por qué quien registró afirma que es el mismo bien. Va en la notificación. */
    @Column(name = "motivo", columnDefinition = "text", nullable = false)
    private String motivo;

    @Column(name = "estado_duplicado", length = 20, nullable = false)
    private String estadoDuplicado;

    /** Quién tenía el sobrante y dónde, al registrarlo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_origen", foreignKey = @ForeignKey(name = "fk_dup_resp_origen"))
    private Responsable responsableOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_origen", foreignKey = @ForeignKey(name = "fk_dup_ofi_origen"))
    private Oficina oficinaOrigen;

    /** La persona a cuyo nombre queda el sobrante en la oficina de duplicados (la misma que lo tenía). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", foreignKey = @ForeignKey(name = "fk_dup_persona"))
    private Persona persona;

    /** La fila de esa persona dentro de la oficina de duplicados (se completa en el ciclo de envío). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_custodia", foreignKey = @ForeignKey(name = "fk_dup_resp_custodia"))
    private Responsable responsableCustodia;

    @Column(name = "estado_envio", length = 20)
    private String estadoEnvio;

    @Column(name = "mensaje_envio", columnDefinition = "text")
    private String mensajeEnvio;

    /**
     * Cuándo empezó el último intento de mover el bien (antes de encolar su ACTUAL, en otra
     * transacción). Si ese intento dejó una orden en la cola y después se deshizo, la orden
     * igual llega al VSIAF: la anulación lo mira para no dejar el VSIAF distinto del SCIAF.
     */
    @Column(name = "intento_envio")
    private LocalDateTime intentoEnvio;

    /** Cuándo se movió el bien en el SCIAF (y se encoló su UPDATE de ACTUAL). */
    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio;

    @Column(name = "usuario_registro", length = 60)
    private String usuarioRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_notificacion", foreignKey = @ForeignKey(name = "fk_dup_notificacion"))
    private NotificacionDuplicado notificacion;

    /** El faltante que se cerró al registrarlo como duplicado (si estaba como faltante). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_hallazgo_faltante", foreignKey = @ForeignKey(name = "fk_dup_hallazgo"))
    private HallazgoInventario hallazgoFaltante;

    /** Paso del envío a faltantes que tenía ese faltante al cerrarlo: se devuelve si se anula el caso. */
    @Column(name = "faltante_estado_envio", length = 20)
    private String faltanteEstadoEnvio;

    /** true = el bien ya estaba en la oficina de duplicados sin caso (se regularizó, no se movió). */
    @Column(name = "regularizado", columnDefinition = "boolean default false", nullable = false)
    private boolean regularizado = false;

    // ── Salida de la oficina de duplicados ──────────────────────────────────

    @Column(name = "fecha_retiro")
    private LocalDateTime fechaRetiro;

    @Column(name = "usuario_retiro", length = 60)
    private String usuarioRetiro;

    /** RETIRADO: por qué se sacó. ANULADO: por qué se anuló. */
    @Column(name = "justificacion_retiro", columnDefinition = "text")
    private String justificacionRetiro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_retiro", foreignKey = @ForeignKey(name = "fk_dup_resp_retiro"))
    private Responsable responsableRetiro;

    public boolean pendiente() {
        return EN_PROCESO.equals(estadoDuplicado) || EN_DUPLICADOS.equals(estadoDuplicado);
    }
}
