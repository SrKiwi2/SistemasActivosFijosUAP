package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "hallazgo_inventario",
    indexes = {
        @Index(name = "idx_hall_inventario", columnList = "id_inventario"),
        @Index(name = "idx_hall_activo", columnList = "id_activo"),
        @Index(name = "idx_hall_tipo", columnList = "tipo_hallazgo"),
        @Index(name = "idx_hall_estado", columnList = "_estado"),
        @Index(name = "idx_hall_responsable", columnList = "id_responsable"),
        @Index(name = "idx_hall_estado_hall", columnList = "estado_hallazgo"),
        @Index(name = "idx_hall_oficina_origen", columnList = "id_oficina_origen")
    }
)
@Setter @Getter
public class HallazgoInventario extends AuditoriaConfig {
    
    private static final long serialVersionUID = 2629195288020321924L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idHallazgo;
    
    /** Levantamiento que lo detectó. Vacío si el faltante se registró directo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_inventario")
    private Inventario inventario;
    
    // FALTANTE: activo en BD pero no encontrado en físico
    // SOBRANTE: activo encontrado en físico pero no en BD
    // SIN_CODIFICAR: activo físico encontrado sin código
    // DESACUERDO_DATOS: activo existe pero con datos diferentes
    @Size(max = 30)
    @Column(name = "tipo_hallazgo", length = 30, nullable = false)
    private String tipoHallazgo;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo")
    private Activo activo;
    
    // Datos del activo físico encontrado (si aplica)
    @Size(max = 60)
    @Column(name = "codigo_fisico", length = 60)
    private String codigoFisico;
    
    @Size(max = 1024)
    @Column(name = "descripcion_fisica", length = 1024)
    private String descripcionFisica;
    
    // Discrepancias identificadas
    @Column(name = "descripcion_discrepancia", columnDefinition = "text")
    private String descripcionDiscrepancia;

    // Acciones correctivas
    @Column(name = "accion_correctiva", columnDefinition = "text")
    private String accionCorrectiva;
    
    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;
    
    // Persona responsable de revisar el hallazgo
    @Size(max = 60)
    @Column(name = "usuario_revisor", length = 60)
    private String usuarioRevisor;
    
    @Column(name = "observ", columnDefinition = "text")
    private String observ;

    // ── Control de activos por responsable ───────────────────────────────────

    /**
     * A quién se le imputa el hallazgo. Se copia del detalle al cerrar el
     * levantamiento y queda fijo: si el activo se transfiere después, el
     * faltante sigue siendo de quien lo tenía ese día.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable")
    private Responsable responsable;

    /** ABIERTO | EN_CUSTODIA | RESUELTO */
    @Size(max = 20)
    @Column(name = "estado_hallazgo", length = 20)
    private String estadoHallazgo;

    /** APARECIO | JUSTIFICADO | DERIVADO_BAJA — con qué se cerró. */
    @Size(max = 30)
    @Column(name = "tipo_resolucion", length = 30)
    private String tipoResolucion;

    // ── Custodia de faltantes ────────────────────────────────────────────────

    /** LEVANTAMIENTO | DIRECTO — por qué puerta entró el hallazgo. */
    @Size(max = 15)
    @Column(name = "origen", length = 15)
    private String origen;

    /**
     * Oficina donde debía estar el bien. Se fija al detectar el hallazgo porque
     * después el activo pasa a la custodia y su oficina actual ya no lo dice; y
     * un faltante directo no tiene levantamiento del cual sacarla.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_origen")
    private Oficina oficinaOrigen;

    /** Nota, informe o memorándum que respalda el faltante (sobre todo el directo). */
    @Size(max = 120)
    @Column(name = "documento_respaldo", length = 120)
    private String documentoRespaldo;

    @Column(name = "fecha_documento")
    private LocalDate fechaDocumento;

    /** Responsable de custodia del predio al que se transfirió el bien en el VSIAF. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_custodia")
    private Responsable responsableCustodia;

    @Column(name = "fecha_envio_custodia")
    private LocalDateTime fechaEnvioCustodia;

    @Size(max = 60)
    @Column(name = "usuario_envio_custodia", length = 60)
    private String usuarioEnvioCustodia;

    /** Acta de faltantes en la que se registró. Vacío mientras sea un faltante ABIERTO sin registrar. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_acta", foreignKey = @jakarta.persistence.ForeignKey(name = "fk_hall_acta"))
    private ActaFaltante acta;

    /**
     * Paso del traslado a la custodia en el VSIAF (null = no se pidió):
     * ESPERANDO_ALTA — el responsable de custodia todavía no está confirmado en el VSIAF;
     * ENVIADO — el bien ya se movió en el SCIAF y su UPDATE de ACTUAL está en la cola;
     * CONFIRMADO — el worker lo aplicó (el hallazgo pasa a EN_CUSTODIA);
     * ERROR — el VSIAF rechazó el alta o el traslado ({@link #mensajeEnvio}).
     */
    @Size(max = 15)
    @Column(name = "estado_envio", length = 15)
    private String estadoEnvio;

    @Column(name = "mensaje_envio", columnDefinition = "text")
    private String mensajeEnvio;
}
