package com.usic.SistemasActivosFijosUAP.model.entity;

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
 * Detalle de un bien donado: una fila por activo.
 * <p>
 * La descripción del activo ({@link Activo#getDescripcion()}, 1024 caracteres) se arma
 * con el mismo orden que el registro normal (descripción, color, marca, modelo, serie)
 * para que el bien se lea igual en todo el sistema. Aquí queda además cada dato por
 * separado, más lo que no entra en esa línea (material, medidas, características), para
 * consultar la donación con todo el detalle con que se recibió.
 */
@Entity
@Table(
    name = "donacion_bien",
    uniqueConstraints = @UniqueConstraint(name = "uk_donacion_bien_activo", columnNames = "id_activo"),
    indexes = @Index(name = "idx_donacion_bien_acta", columnList = "id_donacion")
)
@Setter
@Getter
public class DonacionBien extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_donacion_bien")
    private Long idDonacionBien;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_donacion", nullable = false, foreignKey = @ForeignKey(name = "fk_donacion_bien_acta"))
    private DonacionActa donacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_activo", nullable = false, foreignKey = @ForeignKey(name = "fk_donacion_bien_activo"))
    private Activo activo;

    /** Número del bien dentro del acta (1, 2, 3…), en el orden en que se cargó. */
    @Column(name = "item")
    private Integer item;

    /** Descripción general, sin los datos técnicos que se le suman en el activo. */
    @Column(name = "descripcion_base", length = 600)
    private String descripcionBase;

    @Column(name = "marca", length = 80)
    private String marca;

    @Column(name = "modelo", length = 80)
    private String modelo;

    @Column(name = "serie", length = 80)
    private String serie;

    @Column(name = "color", length = 60)
    private String color;

    @Column(name = "material", length = 80)
    private String material;

    @Column(name = "dimensiones", length = 80)
    private String dimensiones;

    /** Características adicionales: texto libre largo. */
    @Column(name = "caracteristicas", columnDefinition = "text")
    private String caracteristicas;

    /** Valor referencial del bien, en Bs. */
    @Column(name = "valor_referencial")
    private Double valorReferencial;
}
