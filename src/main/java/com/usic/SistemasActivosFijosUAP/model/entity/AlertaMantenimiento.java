package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;
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
    name = "alerta_mantenimiento",
    indexes = {
        @Index(name = "idx_alerta_activo", columnList = "id_activo"),
        @Index(name = "idx_alerta_fecha_alerta", columnList = "fecha_alerta"),
        @Index(name = "idx_alerta_estado", columnList = "_estado"),
        @Index(name = "idx_alerta_fecha_vencimiento", columnList = "fecha_vencimiento")
    }
)
@Setter @Getter
public class AlertaMantenimiento extends AuditoriaConfig{
    private static final long serialVersionUID = 2629195288020321924L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAlerta;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_activo", nullable = false)
    private Activo activo;
    
    // Fecha en que vence el mantenimiento
    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;
    
    // Fecha en que se generó la alerta (típicamente días antes del vencimiento)
    @Column(name = "fecha_alerta", nullable = false)
    private LocalDateTime fechaAlerta;
    
    // Tipo de alerta: PREVENTIVO_VENCIDO, CORRECTIVO_PENDIENTE
    @Size(max = 30)
    @Column(name = "tipo_alerta", length = 30)
    private String tipoAlerta;
    
    @Column(name = "observ", columnDefinition = "text")
    private String observ;
    
    // Registra cuándo se notificó al responsable
    @Column(name = "fecha_notificacion")
    private LocalDateTime fechaNotificacion;
    
    // Prioridad de la alerta: BAJA, MEDIA, ALTA, CRITICA
    @Size(max = 10)
    @Column(name = "prioridad", length = 10)
    private String prioridad;
    
    // Persona u oficina a notificar
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable")
    private Responsable responsable;
    
    // Referencia al mantenimiento asociado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_mantenimiento")
    private Mantenimiento mantenimiento;
}
