package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "entidad", uniqueConstraints = @UniqueConstraint(name = "uk_entidad_gestion_codigo", columnNames = {
        "gestion", "entidad_codigo" }), indexes = {
                @Index(name = "idx_entidad_gestion", columnList = "gestion"),
                @Index(name = "idx_entidad_codigo", columnList = "entidad_codigo")
        })
@Setter
@Getter
public class Entidad extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idEntidad;
    
    @NotNull
    @Column(name = "gestion", nullable = false, columnDefinition = "SMALLINT")
    private Short gestion; // DBF: GESTION (SmallInt)

    @NotBlank
    @Size(max = 30) // su DBF “ENTIDAD” es Text (aunque venga algo tipo 148); lo tratamos como
                    // String
    @Column(name = "entidad_codigo", length = 30, nullable = false)
    private String entidadCodigo; // DBF: ENTIDAD (Text)

    @NotBlank
    @Size(max = 255)
    @Column(name = "desc_ent", length = 255, nullable = false)
    private String descripcion; // DBF: DESC_ENT (Text)

    @Size(max = 30)
    @Column(name = "sigla_ent", length = 30)
    private String sigla; // DBF: SIGLA_ENT (Text)

    @Column(name = "sector_ent", columnDefinition = "SMALLINT")
    private Short sectorEnt; // DBF: SECTOR_ENT (SmallInt)

    @Column(name = "subsec_ent", columnDefinition = "SMALLINT")
    private Short subsecEnt; // DBF: SUBSEC_ENT (SmallInt)

    @Column(name = "area_ent", columnDefinition = "SMALLINT")
    private Short areaEnt; // DBF: AREA_ENT (SmallInt)

    @Column(name = "subarea_ent", columnDefinition = "SMALLINT")
    private Short subareaEnt; // DBF: SUBAREAENT (SmallInt)

    @Column(name = "nivel_inst", columnDefinition = "SMALLINT")
    private Short nivelInst; // DBF: NIVEL_INST (SmallInt)

    // NUEVO: Campos de control de sincronización
    @Column(name = "fecha_ultima_sync")
    private LocalDateTime fechaUltimaSync;
    
    @Column(name = "hash_datos")
    private String hashDatos;  // Para detectar cambios reales
    
    // Método para calcular hash de los datos importantes
    public String calcularHash() {
        String datos = String.join("|", 
            String.valueOf(gestion),
            entidadCodigo,
            descripcion != null ? descripcion : "",
            sigla != null ? sigla : ""
            // incluir otros campos relevantes
        );
        return DigestUtils.md5Hex(datos);
    }
}
