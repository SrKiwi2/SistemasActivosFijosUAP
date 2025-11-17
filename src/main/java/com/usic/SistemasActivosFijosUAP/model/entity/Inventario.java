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
    name = "inventario",
    indexes = {
        @Index(name = "idx_inv_fecha", columnList = "fecha_inicio"),
        @Index(name = "idx_inv_estado", columnList = "_estado"),
        @Index(name = "idx_inv_oficina", columnList = "id_oficina")
    }
)
@Setter @Getter
public class Inventario extends AuditoriaConfig {
    
    private static final long serialVersionUID = 2629195288020321924L;
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idInventario;
    
    @Column(name = "numero_inventario", nullable = false, unique = true)
    private String numeroInventario;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_oficina", nullable = false)
    private Oficina oficina;
    
    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;
    
    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;
    
    // ESTADOS: PLANIFICADO, EN_EJECUCION, COMPLETADO, CONCILIADO
    @Size(max = 20)
    @Column(name = "estado", length = 20, nullable = false)
    private String estado;
    
    @Column(name = "descripcion", columnDefinition = "text")
    private String descripcion;
    
    // Total de activos esperados en la oficina
    private Integer totalActivosEsperados;
    
    // Total de activos encontrados en el inventario
    private Integer totalActivosEncontrados;
    
    // Archivos de soporte
    @Size(max = 500)
    @Column(name = "ruta_archivo_carga", length = 500)
    private String rutaArchivoCarga;
    
    @Column(name = "fecha_carga_archivo")
    private LocalDateTime fechaCargaArchivo;
    
    // Responsables de la ejecución
    @Size(max = 500)
    @Column(name = "responsables_inventario", length = 500)
    private String responsablesInventario;
    
    // Observaciones del proceso
    @Column(name = "observ", columnDefinition = "text")
    private String observ;
}
