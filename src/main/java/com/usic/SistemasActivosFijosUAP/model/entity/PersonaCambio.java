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
 * Historial de cambios de una persona y de sus filas de responsable (una por oficina en
 * RESP.DBF): cargo, nombre, C.I. y unificaciones. Un cambio que toca varias filas comparte
 * el mismo {@code lote}; con el antes y el después se puede revertir.
 * Ver docs/PLAN_PERSONAS_UNIFICADAS.md.
 */
@Entity
@Table(name = "persona_cambio", indexes = {
        @Index(name = "idx_pcambio_persona", columnList = "id_persona"),
        @Index(name = "idx_pcambio_lote", columnList = "lote"),
        @Index(name = "idx_pcambio_responsable", columnList = "id_responsable")
})
@Getter
@Setter
public class PersonaCambio {

    /** Cambio de cargo de una fila. */
    public static final String TIPO_CARGO = "CARGO";
    /** Nombre y/o C.I. de la persona (desde Personas, Responsables o la ficha). */
    public static final String TIPO_DATOS = "NOMBRE_CI";
    /** La fila pasó de una persona a otra al unificarlas. */
    public static final String TIPO_FUSION_FILA = "FUSION_FILA";
    /** La persona quedó unificada en otra (estado FUSIONADA). */
    public static final String TIPO_FUSION_PERSONA = "FUSION_PERSONA";
    /** Cargos juntados en uno (pestaña Cargos): registro del lote, sin persona (id_persona 0). */
    public static final String TIPO_CARGO_FUSION = "CARGO_FUSION";
    /** La fila pasó a otra persona (fase 6: repartir «SIN DATOS», separar homónimos). */
    public static final String TIPO_SEPARACION = "SEPARACION";
    /** Persona creada como destino de una separación (al revertir se da de baja). */
    public static final String TIPO_PERSONA_NUEVA = "PERSONA_NUEVA";
    /** Persona sin oficinas dada de baja desde «Por arreglar» (fase 8). */
    public static final String TIPO_BAJA_PERSONA = "BAJA_PERSONA";
    /** Prefijo de lo que hace una reversión (REVERSION+CARGO, REVERSION+FUSION_FILA…). */
    public static final String TIPO_REVERSION = "REVERSION";

    /** Estado del envío al VSIAF de la fila. */
    public static final String VSIAF_ENVIADO = "ENVIADO";
    public static final String VSIAF_FALLO = "FALLO";
    /** La fila no estaba en el VSIAF (pendiente o rechazada): su alta leerá el dato vigente. */
    public static final String VSIAF_PENDIENTE_ALTA = "PENDIENTE_ALTA";
    /** Cambio que no viaja al VSIAF (p. ej. la persona sobrante de una unificación). */
    public static final String VSIAF_NO_APLICA = "NO_APLICA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cambio")
    private Long idCambio;

    @Column(name = "lote", length = 36, nullable = false)
    private String lote;

    /** Persona a la que pertenece el cambio (la principal, en una unificación). */
    @Column(name = "id_persona", nullable = false)
    private Long idPersona;

    @Column(name = "id_responsable")
    private Long idResponsable;

    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;

    /** Fila del VSIAF: «CULP 184 · cód. 3», o la persona si no es de una fila. */
    @Column(name = "referencia", length = 150)
    private String referencia;

    /** JSON con persona, nombre, C.I. y cargo antes del cambio. */
    @Column(name = "antes", columnDefinition = "text")
    private String antes;

    @Column(name = "despues", columnDefinition = "text")
    private String despues;

    @Column(name = "motivo", length = 500)
    private String motivo;

    /** Pantalla desde donde se hizo: PERSONAS_CARGOS, PERSONA, RESPONSABLE. */
    @Column(name = "origen", length = 30)
    private String origen;

    @Column(name = "id_solicitud")
    private Long idSolicitud;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    /** En las entradas de una reversión: el lote que se revirtió. */
    @Column(name = "lote_revertido", length = 36)
    private String loteRevertido;

    /** En las entradas de un lote ya revertido: el lote de la reversión. */
    @Column(name = "revertido_por", length = 36)
    private String revertidoPor;

    @Column(name = "estado_vsiaf", length = 20)
    private String estadoVsiaf;

    @Column(name = "detalle_vsiaf", length = 500)
    private String detalleVsiaf;
}
