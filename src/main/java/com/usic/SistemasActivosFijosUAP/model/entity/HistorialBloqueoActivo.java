package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "historial_bloqueo_activo", indexes = {
        @Index(name = "idx_hist_bloq_activo", columnList = "id_activo"),
        @Index(name = "idx_hist_bloq_responsable", columnList = "id_responsable"),
        @Index(name = "idx_hist_bloq_fecha", columnList = "fecha")
})
@Setter
@Getter
public class HistorialBloqueoActivo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idHistorialBloqueo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo", nullable = false)
    private Activo activo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable", nullable = false)
    private Responsable responsable;

    @Column(name = "accion", length = 20, nullable = false)
    private String accion;

    @Column(name = "usuario", length = 60, nullable = false)
    private String usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;
}