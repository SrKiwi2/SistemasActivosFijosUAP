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
 * Detalle de un activo adicional (Administración de activos → Adicionales): bienes que no
 * estaban en el sistema y se registran con lo que se sepa de ellos. Ningún dato es
 * obligatorio; el activo ({@link Activo}, procedencia ADICIONAL) existe solo en el SCIAF.
 * <p>
 * Aquí queda lo que no tiene lugar en {@code activo}: el predio elegido sin oficina (forma
 * el código), la descripción sin los datos técnicos y cada dato técnico por separado.
 */
@Entity
@Table(
    name = "activo_adicional",
    uniqueConstraints = @UniqueConstraint(name = "uk_activo_adicional_activo", columnNames = "id_activo"),
    indexes = @Index(name = "idx_activo_adicional_token", columnList = "token_registro")
)
@Setter
@Getter
public class ActivoAdicional extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_adicional")
    private Long idAdicional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_activo", nullable = false, foreignKey = @ForeignKey(name = "fk_activo_adicional_activo"))
    private Activo activo;

    /** Predio del bien cuando no tiene oficina (con oficina, manda el predio de la oficina). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_predio", foreignKey = @ForeignKey(name = "fk_activo_adicional_predio"))
    private Predio predio;

    /** Auxiliar histórico propio del módulo; el auxiliar VSIAF del activo queda intacto en registros anteriores. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_auxiliar_adicional")
    private AuxiliarAdicional auxiliarAdicional;

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

    @Column(name = "caracteristicas", columnDefinition = "text")
    private String caracteristicas;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    /**
     * Marca del intento de registro: si la respuesta se pierde y se vuelve a pulsar Guardar,
     * devuelve lo ya creado en vez de duplicarlo (varias filas comparten el token de un alta).
     */
    @Column(name = "token_registro", length = 40)
    private String tokenRegistro;

    @Column(name = "usuario", length = 60)
    private String usuario;
}
