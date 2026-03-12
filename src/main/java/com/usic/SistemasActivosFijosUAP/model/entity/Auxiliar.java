package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.apache.commons.codec.digest.DigestUtils;

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
@Table(
    name = "auxiliar",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_aux_predio_grupo_codaux", columnNames = {"id_predio", "id_grupo_contable", "cod_aux"})
    },
    indexes = {
        @Index(name = "idx_aux_predio", columnList = "id_predio"),
        @Index(name = "idx_aux_grupo", columnList = "id_grupo_contable"),
        @Index(name = "idx_aux_predio_grupo_codaux", columnList = "id_predio,id_grupo_contable,cod_aux"),
        @Index(name = "idx_aux_codaux", columnList = "cod_aux"),
        @Index(name = "idx_aux_nombre", columnList = "nombre"),
        @Index(name = "idx_aux_sync_fecha", columnList = "fecha_ultima_sync"),
        @Index(name = "idx_aux_hash", columnList = "hash_datos")
    }
)
@Setter @Getter
public class Auxiliar extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAuxiliar;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_predio", nullable = false,
            foreignKey = @ForeignKey(name = "fk_aux_predio"))
    private Predio predio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo_contable", nullable = false,
            foreignKey = @ForeignKey(name = "fk_aux_grupo_contable"))
    private GrupoContable grupoContable;

    @Column(name = "cod_aux", columnDefinition = "SMALLINT", nullable = false)
    private Short codAux;

    @Size(max = 255)
    @Column(name = "nombre", length = 255, nullable = false)
    private String nombre;

    @Column(name = "observ", columnDefinition = "text")
    private String observ;

    @Column(name = "fecha_ult")
    private LocalDate fechaUlt;

    @Size(max = 60)
    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;
    
    @Column(name = "hash_datos", length = 32)
    private String hashDatos;
    
    public String calcularHash() {
        String datos = String.join("|",
            predio != null && predio.getIdPredio() != null 
                ? String.valueOf(predio.getIdPredio()) : "",
            grupoContable != null && grupoContable.getIdGrupoContable() != null 
                ? String.valueOf(grupoContable.getIdGrupoContable()) : "",
            codAux != null ? String.valueOf(codAux) : "",
            nombre != null ? nombre : "",
            observ != null ? observ : "",
            fechaUlt != null ? fechaUlt.toString() : "",
            usuario != null ? usuario : ""
        );
        return DigestUtils.md5Hex(datos);
    }
}
