package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "auxiliar",
    uniqueConstraints = {
        // Si en tu DBF CODAUX es único por UNIDAD (Predio), esta UC es suficiente:
        @UniqueConstraint(name = "uk_aux_predio_codaux", columnNames = {"id_predio", "cod_aux"})
        // Si necesitas que también cuente el grupo contable, usa esta en cambio:
        // @UniqueConstraint(name = "uk_aux_predio_grupo_codaux", columnNames = {"id_predio", "id_grupo_contable", "cod_aux"})
    },
    indexes = {
        @Index(name = "idx_aux_predio", columnList = "id_predio"),
        @Index(name = "idx_aux_grupo", columnList = "id_grupo_contable"),
        @Index(name = "idx_aux_codaux", columnList = "cod_aux"),
        @Index(name = "idx_aux_nombre", columnList = "nombre")
    }
)
@Setter @Getter
public class Auxiliar extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAuxiliar;

    // DBF: ENTIDAD + UNIDAD  -> Predio (ya existente)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_predio", nullable = false,
            foreignKey = @ForeignKey(name = "fk_aux_predio"))
    private Predio predio;

    // DBF: CODCONT -> GrupoContable (ya importado)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo_contable", nullable = false,
            foreignKey = @ForeignKey(name = "fk_aux_grupo_contable"))
    private GrupoContable grupoContable;

    // DBF: CODAUX (SmallInt)
    @Column(name = "cod_aux", columnDefinition = "SMALLINT", nullable = false)
    private Short codAux;

    // DBF: NOMAUX (Text)
    @Size(max = 255)
    @Column(name = "nombre", length = 255, nullable = false)
    private String nombre;

    // DBF: OBSERV (Memo)
    @Lob
    @Column(name = "observ", columnDefinition = "text")
    private String observ;

    // DBF: FEULT (Date)
    @Column(name = "fecha_ult")
    private LocalDate fechaUlt;

    // DBF: USUAR (Text)
    @Size(max = 60)
    @Column(name = "usuario", length = 60)
    private String usuario;
}
