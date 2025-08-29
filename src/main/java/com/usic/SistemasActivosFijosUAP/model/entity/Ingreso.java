package com.usic.SistemasActivosFijosUAP.model.entity;

import java.util.ArrayList;
import java.util.List;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(name = "ingreso")
public class Ingreso extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idIngreso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_propietario")
    private Responsable responsablePropietario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_autorizador")
    private Responsable responsableAutoriza;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_propietario")
    private Oficina oficinaPropietario;

    private String fechaIngreso;
    private String fechaFin;

    @OneToMany(mappedBy = "ingreso", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IngresoDetalle> detalles = new ArrayList<>();
}