package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "predio", uniqueConstraints = @UniqueConstraint(name = "uk_predio_entidad_unidad", columnNames = {
        "entidad_id", "unidad" }), indexes = {
                @Index(name = "idx_predio_unidad", columnList = "unidad"),
                @Index(name = "idx_predio_ciudad", columnList = "ciudad")
        })
@Setter
@Getter
public class Predio extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idPredio;

    // 🔗 El DBF trae ENTIDAD (código). Lo resolvemos a FK Entidad (por código y
    // gestión).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entidad_id", nullable = false, foreignKey = @ForeignKey(name = "fk_predio_entidad"))
    private Entidad entidad;

    // (Opcional) si más adelante quieres mapear ciudad→municipio, déjalo nullable.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_municipio", foreignKey = @ForeignKey(name = "fk_predio_municipio"))
    private Municipio municipio;

    // DBF: UNIDAD (Text)
    @NotBlank
    @Size(max = 30)
    @Column(name = "unidad", length = 30, nullable = false)
    private String unidad;

    // DBF: DESCRIP (Text)
    @NotBlank
    @Size(max = 255)
    @Column(name = "descrip", length = 255, nullable = false)
    private String descrip;

    // DBF: CIUDAD (Text) — puede venir vacío
    @Size(max = 120)
    @Column(name = "ciudad", length = 120)
    private String ciudad;

    // DBF: ESTADOUNI (SmallInt)
    @Column(name = "estado_uni", columnDefinition = "SMALLINT")
    private Short estadoUni;
}