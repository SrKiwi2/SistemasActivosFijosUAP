package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import groovyjarjarpicocli.CommandLine.Help.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
}
