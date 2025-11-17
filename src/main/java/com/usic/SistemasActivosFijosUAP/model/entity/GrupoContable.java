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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "grupo_contable", uniqueConstraints = {
        @UniqueConstraint(name = "uk_grp_codcont", columnNames = { "cod_contable" })
}, indexes = {
        @Index(name = "idx_grp_nombre", columnList = "nombre"),
        @Index(name = "idx_grp_sync_fecha", columnList = "fecha_ultima_sync"),
        @Index(name = "idx_grp_hash", columnList = "hash_datos")
})
@Setter
@Getter
public class GrupoContable extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idGrupoContable;
    private Integer codContable;
    private String nombre;
    private Integer vidaUtil;
    private Boolean depreciar;
    private Boolean actualizar;

    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;
    
    @Column(name = "hash_datos", length = 32)
    private String hashDatos;
    
    /**
     * Calcula hash MD5 de los datos importantes para detectar cambios
     */
    public String calcularHash() {
        String datos = String.join("|",
            codContable != null ? String.valueOf(codContable) : "",
            nombre != null ? nombre : "",
            vidaUtil != null ? String.valueOf(vidaUtil) : "",
            depreciar != null ? String.valueOf(depreciar) : "",
            actualizar != null ? String.valueOf(actualizar) : ""
        );
        return DigestUtils.md5Hex(datos);
    }
}