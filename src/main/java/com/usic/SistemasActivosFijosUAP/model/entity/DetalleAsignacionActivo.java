package com.usic.SistemasActivosFijosUAP.model.entity;

import java.math.BigDecimal;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
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
    @JoinColumn(name = "id_asignacion_activo")
    private AsignacionActivo asignacionActivo;

    @ManyToOne
    @JoinColumn(name = "id_activo")
    private Activo activo;
    
    private String codigoActivoSnapshot;

    @Column(name = "descripcion_activo_snapshot", length = 300)
    private String descripcionActivoSnapshot; // ¡FALTABA!

    @Column(name = "costo_activo_snapshot", precision = 18, scale = 2)
    private BigDecimal costoActivoSnapshot; // ¡FALTABA! Usar BigDecimal para moneda

    @Column(name = "estado_activo_snapshot", length = 50)
    private String estadoActivoSnapshot; // ¡FALTABA! (BUENO, MALO, etc.)

    @Column(name = "observacion_detalle", length = 500)
    private String observacionDetalle; // ¡FALTABA!
}
