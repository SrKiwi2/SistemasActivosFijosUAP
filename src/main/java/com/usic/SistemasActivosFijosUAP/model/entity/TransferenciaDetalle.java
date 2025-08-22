package com.usic.SistemasActivosFijosUAP.model.entity;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
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

@Setter @Getter
@Entity
@Table(name = "transferencia_detalle")
public class TransferenciaDetalle extends AuditoriaConfig{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transferencia", nullable = false)
    private Transferencia transferencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo", nullable = false)
    private Activo activo;

    // Para auditoría fina de antes/después:
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_anterior")
    private Oficina oficinaAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_anterior")
    private Responsable responsableAnterior;

    // NUEVO: ubicaciones declaradas en el formulario
    @Column(name = "ubicacion_origen", length = 200)
    private String ubicacionOrigen; 

    @Column(name = "ubicacion_actual", length = 200)
    private String ubicacionActual;
}
