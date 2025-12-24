package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "detalle_asignacion")
@Setter @Getter
public class DetalleAsignacionActivo extends AuditoriaConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalle;

    @ManyToOne
    @JoinColumn(name = "id_asignacion")
    private AsignacionActivo asignacionActivo;

    @ManyToOne
    @JoinColumn(name = "id_activo")
    private Activo activo;
    
    // Guardamos 'snapshot' del estado en ese momento
    private String codigoActivoSnapshot;
}
