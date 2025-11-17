package com.usic.SistemasActivosFijosUAP.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "revaluacion",
    indexes = {
        @Index(name = "idx_reval_activo", columnList = "id_activo"),
        @Index(name = "idx_reval_fecha", columnList = "fecha_revaluacion"),
        @Index(name = "idx_reval_estado", columnList = "_estado")
    }
)
@Setter @Getter
public class Revaluacion extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idRevaluacion;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_activo", nullable = false)
    private Activo activo;
    
    @Column(name = "fecha_revaluacion", nullable = false)
    private LocalDateTime fechaRevaluacion;
    
    // Valores anterior y nuevo
    private Double valorAnterior;
    private Double valorNuevo;
    
    // Diferencia de valor
    private Double diferencia;
    
    // Porcentaje de cambio
    @Column(precision = 7, scale = 2)
    private BigDecimal porcentajeDecambio;
    
    // TIPOS: AJUSTE_UFV, REEVALUACION_TECNICA, DEPRECIACION_ACELERADA, OTROS
    @Size(max = 30)
    @Column(name = "tipo_revaluacion", length = 30)
    private String tipoRevaluacion;
    
    @Column(name = "motivo", columnDefinition = "text")
    private String motivo;
    
    @Column(name = "justificacion_tecnica", columnDefinition = "text")
    private String justificacionTecnica;
    
    // Información del documento que respalda la revaluación
    @Size(max = 500)
    @Column(name = "documento_referencia", length = 500)
    private String documentoReferencia;
    
    // Persona que solicita y aprueba
    @Size(max = 60)
    @Column(name = "usuario_solicitante", length = 60)
    private String usuarioSolicitante;
    
    @Size(max = 60)
    @Column(name = "usuario_aprobador", length = 60)
    private String usuarioAprobador;
    
    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;
    
    @Column(name = "observ", columnDefinition = "text")
    private String observ;
}
