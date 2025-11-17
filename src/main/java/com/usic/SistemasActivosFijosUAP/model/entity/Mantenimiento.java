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
    name = "mantenimiento",
    indexes = {
        @Index(name = "idx_mant_activo", columnList = "id_activo"),
        @Index(name = "idx_mant_fecha", columnList = "fecha_mantenimiento"),
        @Index(name = "idx_mant_tipo", columnList = "tipo_mantenimiento"),
        @Index(name = "idx_mant_responsable_tecnico", columnList = "responsable_tecnico"),
        @Index(name = "idx_mant_proxima_fecha", columnList = "proxima_fecha_mantenimiento")
    }
)
@Setter @Getter
public class Mantenimiento extends AuditoriaConfig {
    private static final long serialVersionUID = 2629195288020321924L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMantenimiento;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_activo", nullable = false)
    private Activo activo;
    
    // TIPOS: PREVENTIVO, CORRECTIVO
    @Size(max = 20)
    @Column(name = "tipo_mantenimiento", length = 20, nullable = false)
    private String tipoMantenimiento;
    
    @Column(name = "fecha_mantenimiento", nullable = false)
    private LocalDateTime fechaMantenimiento;
    
    @Size(max = 60)
    @Column(name = "responsable_tecnico", length = 60)
    private String responsableTecnico;
    
    @Column(name = "observ", columnDefinition = "text")
    private String observ;
    
    // Para mantenimiento correctivo: descripción del problema
    @Column(name = "descripcion_problema", columnDefinition = "text")
    private String descripcionProblema;
    
    // Para mantenimiento correctivo: descripción de la solución
    @Column(name = "descripcion_solucion", columnDefinition = "text")
    private String descripcionSolucion;
    
    // Costo del mantenimiento
    private Double costo;
    
    // Próxima fecha sugerida de mantenimiento (para preventivos)
    @Column(name = "proxima_fecha_mantenimiento")
    private LocalDate proximaFechaMantenimiento;
    
    // Duración en horas del trabajo
    private Integer duracionHoras;
    
    // Piezas reemplazadas (descripción)
    @Column(name = "piezas_reemplazadas", columnDefinition = "text")
    private String piezasReemplazadas;
    
    // Referencia a orden de trabajo o ticket
    @Size(max = 100)
    @Column(name = "numero_ticket", length = 100)
    private String numeroTicket;
}
