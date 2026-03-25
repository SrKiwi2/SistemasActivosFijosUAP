package com.usic.SistemasActivosFijosUAP.model.entity;

import java.math.BigDecimal;

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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter @Getter @NoArgsConstructor
@Entity
@Table(name = "transferencia_detalle")
public class TransferenciaDetalle extends AuditoriaConfig{
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long idDetalle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transferencia", nullable = false)
    private Transferencia transferencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo", nullable = false)
    private Activo activo;

    @Column(name = "codigo_activo", length = 50, nullable = false)
    private String codigoActivo;
 
    @Column(name = "descripcion_activo", length = 300)
    private String descripcionActivo;
 
    @Column(name = "costo_activo", precision = 18, scale = 2)
    private BigDecimal costoActivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_anterior")
    private Oficina oficinaAnterior;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_anterior")
    private Responsable responsableAnterior;

    @Column(name = "ubicacion_origen", length = 200)
    private String ubicacionOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_destino")
    private Oficina oficinaDestino;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_destino")
    private Responsable responsableDestino;

    @Column(name = "ubicacion_actual", length = 200)
    private String ubicacionActual;

    @Column(name = "observacion_detalle", length = 500)
    private String observacionDetalle;
}
