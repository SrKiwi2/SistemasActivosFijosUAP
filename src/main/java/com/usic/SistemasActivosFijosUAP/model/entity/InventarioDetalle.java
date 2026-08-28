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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Una línea de la lista esperada de un levantamiento: un activo que, según la
 * base de datos, debería estar en esa oficina.
 *
 * <p>Se congela al abrir el levantamiento. Es lo que hace posible detectar los
 * faltantes <b>por ausencia</b>: en campo solo se marca lo que se encuentra, y
 * al cerrar todo lo que quedó en {@link #SITUACION_PENDIENTE} pasa a
 * {@link #SITUACION_FALTANTE}. Sin esta lista previa no habría contra qué
 * comparar — un activo que nadie miró sería indistinguible de uno ausente.
 *
 * <p>El código, la descripción y el responsable se guardan como copia y no por
 * la relación: el acta debe seguir diciendo lo que decía el día del recorrido
 * aunque el activo se transfiera o se renombre después.
 */
@Entity
@Table(
    name = "inventario_detalle",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_invdet_inventario_activo",
        columnNames = { "id_inventario", "id_activo" }
    ),
    indexes = {
        @Index(name = "idx_invdet_inventario",  columnList = "id_inventario"),
        @Index(name = "idx_invdet_situacion",   columnList = "id_inventario, situacion"),
        @Index(name = "idx_invdet_activo",      columnList = "id_activo"),
        @Index(name = "idx_invdet_responsable", columnList = "id_responsable")
    }
)
@Setter
@Getter
public class InventarioDetalle extends AuditoriaConfig {

    private static final long serialVersionUID = 2629195288020321926L;

    /** Esperado pero todavía no revisado. Al cerrar se convierte en FALTANTE. */
    public static final String SITUACION_PENDIENTE = "PENDIENTE";
    /** Confirmado físicamente en la oficina. */
    public static final String SITUACION_ENCONTRADO = "ENCONTRADO";
    /** No apareció: genera un HallazgoInventario tipo FALTANTE al cerrar. */
    public static final String SITUACION_FALTANTE = "FALTANTE";

    public static final String ORIGEN_ESCANEO = "ESCANEO";
    public static final String ORIGEN_MANUAL  = "MANUAL";
    public static final String ORIGEN_WEB     = "WEB";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_inventario", nullable = false,
                foreignKey = @ForeignKey(name = "fk_invdet_inventario"))
    private Inventario inventario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_activo", nullable = false,
                foreignKey = @ForeignKey(name = "fk_invdet_activo"))
    private Activo activo;

    /** Responsable al que estaba imputado el activo al abrir el levantamiento. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable",
                foreignKey = @ForeignKey(name = "fk_invdet_responsable"))
    private Responsable responsable;

    // ── Copia del estado del activo al abrir ─────────────────────────────────

    @Size(max = 60)
    @Column(name = "codigo", length = 60)
    private String codigo;

    @Size(max = 1024)
    @Column(name = "descripcion", length = 1024)
    private String descripcion;

    // ── Resultado del recorrido ──────────────────────────────────────────────

    /** PENDIENTE | ENCONTRADO | FALTANTE */
    @Size(max = 15)
    @Column(name = "situacion", length = 15, nullable = false)
    private String situacion = SITUACION_PENDIENTE;

    /** ESCANEO | MANUAL | WEB — cómo se marcó. */
    @Size(max = 10)
    @Column(name = "origen_marca", length = 10)
    private String origenMarca;

    /**
     * Momento de la marca <b>según el dispositivo</b>, no según el servidor: en
     * campo se trabaja sin señal y el lote puede llegar horas después. También
     * es el criterio para no pisar una marca más nueva cuando la app reenvía.
     */
    @Column(name = "fecha_marca")
    private LocalDateTime fechaMarca;

    /**
     * Novedad anotada en campo: "está roto", "le falta una rueda", "el número de
     * serie no coincide con la etiqueta". Es texto libre a propósito — quien
     * recorre no debería pelear con un formulario para dejar constancia.
     */
    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    /**
     * Condición física constatada en el recorrido, del catálogo {@code estado_activo}.
     * Se guarda aparte del estado que el activo tiene registrado: la diferencia
     * entre ambos es justamente el hallazgo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_observado",
                foreignKey = @ForeignKey(name = "fk_invdet_estado_obs"))
    private EstadoActivo estadoObservado;

    public boolean estaPendiente() {
        return SITUACION_PENDIENTE.equals(situacion);
    }

    /**
     * ¿El recorrido dejó una novedad sobre este activo? Un encontrado con
     * observación o con una condición distinta a la registrada también es un
     * hallazgo que alguien debe atender, no solo los que no aparecieron.
     */
    public boolean tieneNovedad() {
        return (observacion != null && !observacion.isBlank()) || estadoObservado != null;
    }
}
