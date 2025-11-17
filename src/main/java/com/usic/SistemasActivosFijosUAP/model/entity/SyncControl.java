package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sync_control")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyncControl {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "tabla_nombre", unique = true, nullable = false, length = 100)
    private String tablaNombre;
    
    @Column(name = "ultima_sincronizacion", nullable = false)
    private LocalDateTime ultimaSincronizacion;
    
    @Column(name = "registros_procesados")
    private Integer registrosProcesados = 0;
    
    @Column(name = "registros_nuevos")
    private Integer registrosNuevos = 0;
    
    @Column(name = "registros_actualizados")
    private Integer registrosActualizados = 0;
    
    @Column(name = "duracion_ms")
    private Long duracionMs;
    
    @Column(name = "estado", length = 50)
    private String estado;
    
    @Column(name = "mensaje_error", columnDefinition = "TEXT")
    private String mensajeError;
}
