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
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "responsable", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resp_oficina_codfunc", columnNames = { "id_oficina", "codigo_funcionario" })
}, indexes = {
        @Index(name = "idx_resp_oficina", columnList = "id_oficina"),
        @Index(name = "idx_resp_codfunc", columnList = "codigo_funcionario")
})
@Setter
@Getter
public class Responsable extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idResponsable;

    private String codigoFuncionario;
    private String codigoApi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_persona", foreignKey = @ForeignKey(name = "fk_resp_persona"))
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oficina", nullable = false, foreignKey = @ForeignKey(name = "fk_resp_oficina"))
    private Oficina oficina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cargo", foreignKey = @ForeignKey(name = "fk_resp_cargo"))
    private Cargo cargo;

    @Column(name = "observ", columnDefinition = "text")
    private String observ; // DBF: OBSERV (Memo)

    @Column(name = "fecha_ult")
    private LocalDate fechaUlt; // DBF: FEULT (Date)

    @Size(max = 60)
    @Column(name = "usuario", length = 60)
    private String usuario; // DBF: USUAR (Text)

    @Column(name = "cod_exp", columnDefinition = "SMALLINT")
    private Short codExp; // DBF: COD_EXP (SmallInt)

    @Column(name = "api_estado", columnDefinition = "SMALLINT")
    private Short apiEstado; // DBF: API_ESTADO (SmallInt)
}