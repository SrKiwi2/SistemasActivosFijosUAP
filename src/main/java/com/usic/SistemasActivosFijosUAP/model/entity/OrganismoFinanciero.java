package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "organismo_financiero",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_orgfin_gestion_codof", columnNames = {"gestion", "cod_of"})
    },
    indexes = {
        @Index(name = "idx_orgfin_gestion", columnList = "gestion"),
        @Index(name = "idx_orgfin_sigla", columnList = "sigla"),
        @Index(name = "idx_orgfin_descripcion", columnList = "descripcion")
    }
)
@Setter @Getter
public class OrganismoFinanciero extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idOrganismoFinanciero;

    // DBF: GESTION
    @Column(name = "gestion", nullable = false, columnDefinition = "SMALLINT")
    private Short gestion;

    // DBF: OF (en DBF viene como Text; lo guardamos como string)
    @NotBlank
    @Size(max = 20)
    @Column(name = "cod_of", length = 20, nullable = false)
    private String codOf;

    // DBF: DES
    @NotBlank
    @Size(max = 255)
    @Column(name = "descripcion", length = 255, nullable = false)
    private String descripcion;

    // DBF: SIGLA
    @Size(max = 60)
    @Column(name = "sigla", length = 60)
    private String sigla;
}
