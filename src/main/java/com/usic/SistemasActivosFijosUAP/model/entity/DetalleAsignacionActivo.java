package com.usic.SistemasActivosFijosUAP.model.entity;

import java.math.BigDecimal;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "detalle_asignacion")
@Setter @Getter
public class DetalleAsignacionActivo extends AuditoriaConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalle;

    @ManyToOne
    @JoinColumn(name = "id_asignacion_activo")
    private AsignacionActivo asignacionActivo;

    @ManyToOne
    @JoinColumn(name = "id_activo")
    private Activo activo;
    
    private String codigoActivoSnapshot;

    @Column(name = "descripcion_activo_snapshot", length = 300)
    private String descripcionActivoSnapshot; // ¡FALTABA!

    @Column(name = "costo_activo_snapshot", precision = 18, scale = 2)
    private BigDecimal costoActivoSnapshot; // ¡FALTABA! Usar BigDecimal para moneda

    @Column(name = "estado_activo_snapshot", length = 50)
    private String estadoActivoSnapshot; // ¡FALTABA! (BUENO, MALO, etc.)

    @Column(name = "observacion_detalle", length = 500)
    private String observacionDetalle; // ¡FALTABA!

    /**
     * Si esta línea sigue siendo la que ubica al activo, o si el bien ya se movió a otra
     * acta.
     * <p>
     * Un activo tiene que estar en exactamente un acta vigente. Sin esta marca, mover un
     * bien obligaría a borrar la línea original —y con ella el costo y la descripción que
     * tenía el acta cuando se firmó— o a dejar el activo en dos actas a la vez, que es lo
     * que hoy nada impide. Con TRASLADADO la línea se conserva como parte del acta
     * original y deja de contar como ubicación actual.
     * <p>
     * Lo blinda un índice único parcial sobre {@code (id_activo) WHERE estado_detalle =
     * 'VIGENTE'}; ver {@code scripts/sql/asignaciones_fase1.sql}.
     */
    @Column(name = "estado_detalle", length = 20)
    private String estadoDetalle = VIGENTE;

    /** Esta línea ubica al activo hoy. */
    public static final String VIGENTE = "VIGENTE";
    /** El activo se movió a otra acta; la línea queda como historia del acta original. */
    public static final String TRASLADADO = "TRASLADADO";

    public boolean estaVigente() {
        return estadoDetalle == null || VIGENTE.equals(estadoDetalle);
    }
}
