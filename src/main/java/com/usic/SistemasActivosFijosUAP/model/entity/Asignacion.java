package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "asignacion")
public class Asignacion extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAsignacion;

    private String unidadResponsable;
    private String hr;
    private String ubicacionActivo;
    private String descripcionActivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsbale")
    private Responsable responsable;
}
