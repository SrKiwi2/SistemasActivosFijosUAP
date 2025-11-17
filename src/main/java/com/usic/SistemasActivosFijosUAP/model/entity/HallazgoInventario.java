package com.usic.SistemasActivosFijosUAP.model.entity;

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
    name = "hallazgo_inventario",
    indexes = {
        @Index(name = "idx_hall_inventario", columnList = "id_inventario"),
        @Index(name = "idx_hall_activo", columnList = "id_activo"),
        @Index(name = "idx_hall_tipo", columnList = "tipo_hallazgo"),
        @Index(name = "idx_hall_estado", columnList = "_estado")
    }
)
@Setter @Getter
public class HallazgoInventario extends AuditoriaConfig {
    
    private static final long serialVersionUID = 2629195288020321924L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idHallazgo;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_inventario", nullable = false)
    private Inventario inventario;
    
    // FALTANTE: activo en BD pero no encontrado en físico
    // SOBRANTE: activo encontrado en físico pero no en BD
    // SIN_CODIFICAR: activo físico encontrado sin código
    // DESACUERDO_DATOS: activo existe pero con datos diferentes
    @Size(max = 30)
    @Column(name = "tipo_hallazgo", length = 30, nullable = false)
    private String tipoHallazgo;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo")
    private Activo activo;
    
    // Datos del activo físico encontrado (si aplica)
    @Size(max = 60)
    @Column(name = "codigo_fisico", length = 60)
    private String codigoFisico;
    
    @Size(max = 1024)
    @Column(name = "descripcion_fisica", length = 1024)
    private String descripcionFisica;
    
    // Discrepancias identificadas
    @Column(name = "descripcion_discrepancia", columnDefinition = "text")
    private String descripcionDiscrepancia;

    // Acciones correctivas
    @Column(name = "accion_correctiva", columnDefinition = "text")
    private String accionCorrectiva;
    
    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;
    
    // Persona responsable de revisar el hallazgo
    @Size(max = 60)
    @Column(name = "usuario_revisor", length = 60)
    private String usuarioRevisor;
    
    @Column(name = "observ", columnDefinition = "text")
    private String observ;
}
