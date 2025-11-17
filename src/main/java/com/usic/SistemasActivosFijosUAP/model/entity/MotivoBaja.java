package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "motivo_baja")
@Setter @Getter
public class MotivoBaja extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMotivoBaja;
    
    @NotBlank
    @Size(max = 100)
    @Column(name = "nombre", length = 100, nullable = false, unique = true)
    private String nombre;
    
    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;
    
    // Código para referencia rápida: OBS, DAÑ, PER, DET
    @Size(max = 10)
    @Column(name = "codigo", length = 10, unique = true)
    private String codigo;
}
