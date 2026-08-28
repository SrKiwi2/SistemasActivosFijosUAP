package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Una operación hecha sobre un acta: separarla, incorporarle bienes, trasladar uno o
 * corregir su cabecera.
 * <p>
 * No alcanza con {@code historial_activo} para esto. Ese registra eventos <em>de un
 * bien</em> —lo usa transferencias— y responde "¿por dónde pasó este activo?". Lo que
 * hace falta acá es lo contrario: "¿por qué esta acta tiene 8 bienes si se firmó con
 * 10?". Esa pregunta es sobre el acta, y sin esta tabla no tiene respuesta.
 * <p>
 * El {@link #motivo} es obligatorio a propósito. Una separación sin explicación deja el
 * mismo agujero que no registrar nada: se sabe que pasó, no por qué.
 */
@Entity
@Table(name = "asignacion_movimiento", indexes = {
    @Index(name = "ix_asig_mov_origen",  columnList = "id_asignacion_origen"),
    @Index(name = "ix_asig_mov_destino", columnList = "id_asignacion_destino")
})
@Getter @Setter @NoArgsConstructor
public class AsignacionMovimiento {

    /** Parte de los bienes salió a un acta nueva. */
    public static final String SEPARACION = "SEPARACION";
    /** Entraron bienes que estaban en otra acta. */
    public static final String INCORPORACION = "INCORPORACION";
    /** Se movió un bien puntual de un acta a otra. */
    public static final String TRASLADO = "TRASLADO";
    /** Cambiaron los datos del acta, no su contenido. */
    public static final String EDICION_CABECERA = "EDICION_CABECERA";
    /** El acta se dejó sin efecto. */
    public static final String ANULACION = "ANULACION";

    /** El VSIAF no intervino: la operación solo reordenó actas del SCIAF. */
    public static final String VSIAF_NO_APLICA = "NO_APLICA";
    /** Se encolaron cambios al VSIAF por esta operación. */
    public static final String VSIAF_ENCOLADO = "ENCOLADO";
    /** Algo del envío al VSIAF falló; el detalle está en {@link #mensajeVsiaf}. */
    public static final String VSIAF_ERROR = "ERROR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento")
    private Long idMovimiento;

    /** Acta sobre la que se actuó. Siempre presente. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_asignacion_origen", nullable = false)
    private AsignacionActivo asignacionOrigen;

    /** Acta que recibió los bienes. Nula en ediciones de cabecera y anulaciones. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_asignacion_destino")
    private AsignacionActivo asignacionDestino;

    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo;

    @Column(name = "motivo", columnDefinition = "text", nullable = false)
    private String motivo;

    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombre_usuario", length = 100)
    private String nombreUsuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha = LocalDateTime.now();

    /**
     * Qué pasó con el VSIAF en esta operación.
     * <p>
     * Separar un acta no toca el sistema legacy —allá no existe el concepto de acta—,
     * pero cambiar el responsable, la oficina o la descripción de los bienes sí. Guardar
     * el resultado permite explicar después por qué un bien quedó desincronizado.
     */
    @Column(name = "resultado_vsiaf", length = 20)
    private String resultadoVsiaf = VSIAF_NO_APLICA;

    @Column(name = "mensaje_vsiaf", columnDefinition = "text")
    private String mensajeVsiaf;

    @OneToMany(mappedBy = "movimiento", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AsignacionMovimientoDetalle> detalles = new ArrayList<>();

    public void agregarDetalle(AsignacionMovimientoDetalle detalle) {
        detalle.setMovimiento(this);
        this.detalles.add(detalle);
    }
}
