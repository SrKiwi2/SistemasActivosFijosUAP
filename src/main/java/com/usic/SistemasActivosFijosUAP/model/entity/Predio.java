package com.usic.SistemasActivosFijosUAP.model.entity;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entidad_id", nullable = false, foreignKey = @ForeignKey(name = "fk_predio_entidad"))
    private Entidad entidad;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "id_municipio", foreignKey = @ForeignKey(name = "fk_predio_municipio"))
    private Municipio municipio;

    @NotBlank
    @Size(max = 30)
    @Column(name = "unidad", length = 30, nullable = false)
    private String unidad;

    @NotBlank
    @Size(max = 255)
    @Column(name = "descrip", length = 255, nullable = false)
    private String descrip;

    @Size(max = 120)
    @Column(name = "ciudad", length = 120)
    private String ciudad;

    @Column(name = "estado_uni", columnDefinition = "SMALLINT")
    private Short estadoUni;

    private String codigo;

    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;
    
    @Column(name = "hash_datos", length = 32)
    private String hashDatos;
    
    public String calcularHash() {
        String datos = String.join("|",
            entidad != null ? String.valueOf(entidad.getIdEntidad()) : "",
            unidad != null ? unidad : "",
            descrip != null ? descrip : "",
            ciudad != null ? ciudad : "",
            estadoUni != null ? String.valueOf(estadoUni) : ""
        );
        return DigestUtils.md5Hex(datos);
    }
}