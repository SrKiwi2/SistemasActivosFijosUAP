package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;

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
@Table(name = "activo")
@Setter
@Getter
public class Activo extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idActivo;

    private String codigo;
    private String nombre;
    private String descripcion;
    private Double costo;
    private Integer vida_util;
    private LocalDate fecha_adquisición;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estado_activo")
    private EstadoActivo estadoActivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_grupo_contable")
    private GrupoContable grupoContable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina")
    private Oficina oficina;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable")
    private Responsable responsable;
}
