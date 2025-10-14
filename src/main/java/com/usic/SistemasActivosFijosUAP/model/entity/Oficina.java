package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "oficina", uniqueConstraints = @UniqueConstraint(name = "uk_oficina_predio_codofi", columnNames = {
        "id_predio", "cod_ofi" }), indexes = {
                @Index(name = "idx_oficina_predio", columnList = "id_predio"),
                @Index(name = "idx_oficina_codofi", columnList = "cod_ofi")
        })
@Setter
@Getter
public class Oficina extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idOficina;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_predio", nullable = false, foreignKey = @ForeignKey(name = "fk_oficina_predio"))
    private Predio predio; // Resuelto por (ENTIDAD, UNIDAD)

    @Column(name = "cod_ofi", columnDefinition = "SMALLINT", nullable = false)
    private Short codOfi; // DBF: CODOFI

    @NotBlank
    @Size(max = 255)
    @Column(name = "nombre", length = 255, nullable = false)
    private String nombre; // DBF: NOMOFIC

    @Column(name = "observ", columnDefinition = "text")
    private String observ; // DBF: OBSERV (Memo)

    @Column(name = "fecha_ult")
    private LocalDate fechaUlt; // DBF: FEULT (Date)

    @Size(max = 60)
    @Column(name = "usuario", length = 60)
    private String usuario; // DBF: USUAR

    @Column(name = "api_estado", columnDefinition = "SMALLINT")
    private Short apiEstado; // DBF: API_ESTADO
}