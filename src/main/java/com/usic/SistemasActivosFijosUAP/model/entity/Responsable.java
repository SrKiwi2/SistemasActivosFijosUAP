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
@Table(name = "responsable", uniqueConstraints = {
        @UniqueConstraint(name = "uk_resp_oficina_codfunc", columnNames = { "id_oficina", "codigo_funcionario" })
}, indexes = {
        @Index(name = "idx_resp_oficina", columnList = "id_oficina"),
        @Index(name = "idx_resp_codfunc", columnList = "codigo_funcionario"),
        @Index(name = "idx_resp_persona", columnList = "id_persona"),
        @Index(name = "idx_resp_sync_fecha", columnList = "fecha_ultima_sync"),
        @Index(name = "idx_resp_hash", columnList = "hash_datos")
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
    private String observ;

    @Column(name = "fecha_ult")
    private LocalDate fechaUlt;

    @Size(max = 60)
    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "cod_exp", columnDefinition = "SMALLINT")
    private Short codExp;

    @Column(name = "api_estado", columnDefinition = "SMALLINT")
    private Short apiEstado;

    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;
    
    @Column(name = "hash_datos", length = 32)
    private String hashDatos;
    
    public String calcularHash() {
        String datos = String.join("|",
            oficina != null && oficina.getIdOficina() != null 
                ? String.valueOf(oficina.getIdOficina()) : "",
            codigoFuncionario != null ? codigoFuncionario : "",
            persona != null && persona.getIdPersona() != null 
                ? String.valueOf(persona.getIdPersona()) : "",
            cargo != null && cargo.getIdCargo() != null 
                ? String.valueOf(cargo.getIdCargo()) : "",
            observ != null ? observ : "",
            fechaUlt != null ? fechaUlt.toString() : "",
            usuario != null ? usuario : "",
            codExp != null ? String.valueOf(codExp) : "",
            apiEstado != null ? String.valueOf(apiEstado) : ""
        );
        return DigestUtils.md5Hex(datos);
    }
}