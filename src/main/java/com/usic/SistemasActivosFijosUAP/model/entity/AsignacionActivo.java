package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "asignacion_activo")
@Setter @Getter
public class AsignacionActivo extends AuditoriaConfig{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAsignacionActivo;

    @Column(name = "numero_asignacion", length = 30, unique = true)
    private String numeroAsignacion;

    @Column(name = "codigo_documento")
    private String codigoDocumento;

    @Column(name = "codigo_completo")
    private String codigoCompleto;
    
    @Column(name = "fecha_asignacion")
    private LocalDateTime fechaAsignacion;

    @Column(name = "tipo_asignacion", length = 20)
    private String tipoAsignacion = "NUEVA";

    @Column(name = "estado_asignacion", length = 30)
    private String estadoAsignacion = "ACTIVA";

    @Column(name = "documento_referencia", length = 100)
    private String documentoReferencia;

    @Column(name = "observacion", columnDefinition = "text")
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable")
    private Responsable responsable;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_responsable_origen")
    private Responsable responsableOrigen;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_destino")
    private Oficina oficinaDestino;

    @OneToMany(mappedBy = "asignacionActivo", cascade = CascadeType.ALL)
    private List<DetalleAsignacionActivo> detalles;
}