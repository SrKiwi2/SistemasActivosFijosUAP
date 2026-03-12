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
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "oficina", uniqueConstraints = @UniqueConstraint(name = "uk_oficina_predio_codofi", columnNames = {
        "id_predio", "cod_ofi" }), indexes = {
                @Index(name = "idx_oficina_predio", columnList = "id_predio"),
                @Index(name = "idx_oficina_codofi", columnList = "cod_ofi"),
                @Index(name = "idx_oficina_sync_fecha", columnList = "fecha_ultima_sync"),
                @Index(name = "idx_oficina_hash", columnList = "hash_datos")
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
    private Predio predio;

    @Column(name = "cod_ofi", columnDefinition = "SMALLINT", nullable = false)
    private Short codOfi;

    @NotBlank
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

    @Column(name = "api_estado", columnDefinition = "SMALLINT")
    private Short apiEstado;

    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;
    
    @Column(name = "hash_datos", length = 32)
    private String hashDatos;
    
    public String calcularHash() {
        String datos = String.join("|",
            predio != null && predio.getIdPredio() != null 
                ? String.valueOf(predio.getIdPredio()) : "",
            codOfi != null ? String.valueOf(codOfi) : "",
            nombre != null ? nombre : "",
            observ != null ? observ : "",
            fechaUlt != null ? fechaUlt.toString() : "",
            usuario != null ? usuario : "",
            apiEstado != null ? String.valueOf(apiEstado) : ""
        );
        return DigestUtils.md5Hex(datos);
    }

    @Transient
    private boolean existeEnDbf = true;

    public boolean isExisteEnDbf() { return existeEnDbf; }
    public void setExisteEnDbf(boolean existeEnDbf) { this.existeEnDbf = existeEnDbf; }
}