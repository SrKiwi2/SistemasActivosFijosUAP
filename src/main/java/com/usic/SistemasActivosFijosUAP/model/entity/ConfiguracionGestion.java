package com.usic.SistemasActivosFijosUAP.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "configuracion_gestion",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_config_gestion_prefijo",
            columnNames = {"gestion", "prefijo_documento"}
        )
    }
)

@Setter
@Getter
@NoArgsConstructor 
public class ConfiguracionGestion{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idConfig;

    private Integer gestion; // 2025
    
    private String prefijoDocumento; // "PREV."
    private String ciudad; // "Cobija"

    @jakarta.persistence.Column(name = "_estado")
    private String estado;
    private String responsableActivosNombre;

    /**
     * ID de la carpeta de Google Drive donde se suben las actas de esta gestión.
     * Administrable y cambia por gestión. Se usa para redirigir a la carpeta.
     */
    @jakarta.persistence.Column(name = "carpeta_drive")
    private String carpetaDrive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_entrega")
    @JsonIgnore
    private ResponsableEntrega responsableEntregaRef;

    @jakarta.persistence.Column(name = "responsable_entrega")
    private String responsableEntrega;
}
