package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import org.apache.commons.codec.digest.DigestUtils;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
        @Index(name = "idx_orgfin_descripcion", columnList = "descripcion"),
        @Index(name = "idx_orgfin_sync_fecha", columnList = "fecha_ultima_sync"),
        @Index(name = "idx_orgfin_hash", columnList = "hash_datos")
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

    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;
    
    @Column(name = "hash_datos", length = 32)
    private String hashDatos;
    
    /**
     * Calcula hash MD5 de los datos importantes para detectar cambios
     */
    public String calcularHash() {
        String datos = String.join("|",
            gestion != null ? String.valueOf(gestion) : "",
            codOf != null ? codOf : "",
            descripcion != null ? descripcion : "",
            sigla != null ? sigla : ""
        );
        return DigestUtils.md5Hex(datos);
    }
}
