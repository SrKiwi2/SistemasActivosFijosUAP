package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transferencia_cabecera")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferenciaCabecera {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCabecera;

    @Column(unique = true, nullable = false)
    private String corrT;           // CORR_T — clave única del grupo

    private String    nombreT;
    private LocalDate fechaT;
    private String    estadoTDbf;

    // Ruta
    private String unidadO;
    private String unidadD;

    // Solicitante (común a todo el grupo)
    private Short  codOficO;        // puede variar por activo, aquí va el del primero
    private String ciSolicitante;

    // Receptor (común a todo el grupo)
    private Short  codOficD;
    private String ciReceptor;
    private String nomReceptor;

    @Enumerated(EnumType.STRING)
    private TransferenciaValidadaDto.TipoTransferencia tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTransferencia estado;

    private LocalDateTime fechaAprobacion;
    private String        usuarioAprobacion;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    // Relación 1:N con los activos del grupo
    @OneToMany(mappedBy = "cabecera",
               cascade = CascadeType.ALL,
               orphanRemoval = true,
               fetch = FetchType.LAZY)
    @Builder.Default
    private List<TransferenciaDetalleLondra> detalles = new ArrayList<>();

    @PrePersist
    protected void onCreate() { this.creadoEn = LocalDateTime.now(); }

    public enum EstadoTransferencia { PENDIENTE, APROBADO, RECHAZADO }
}
