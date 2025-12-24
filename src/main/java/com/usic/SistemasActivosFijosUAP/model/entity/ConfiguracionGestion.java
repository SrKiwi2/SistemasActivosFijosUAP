package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "configuracion_gestion")
@Setter @Getter
public class ConfiguracionGestion extends AuditoriaConfig{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idConfig;
    
    @Column(unique = true)
    private Integer gestion; // 2025
    
    private String prefijoDocumento; // "PREV."
    private String ciudad; // "Cobija"
    private String responsableActivosNombre;
}
