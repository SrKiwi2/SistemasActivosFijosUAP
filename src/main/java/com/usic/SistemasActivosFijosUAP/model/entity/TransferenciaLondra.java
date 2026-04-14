package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transferencias")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferenciaLondra {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idTransferencia;

    // Origen DBF
    @Column(unique = true)
    private String  corrT;
    private Long    idTDbf;
    private String  nombreT;
    private LocalDate fechaT;
    private String  estadoTDbf;

    @Enumerated(EnumType.STRING)
    private TransferenciaValidadaDto.TipoTransferencia tipo;

    // Origen
    private String  unidadO;
    private Short   codContO;
    private Short   codAuxO;
    private String  codigoActivo;   // CODIGO_O
    private Short   estadoActivoO;
    private Short   codOficO;
    private Short   codRespO;
    private String  ciSolicitante;

    // Destino
    private String  unidadD;
    private Short   codOficD;
    private String  ciReceptor;
    private String  nomReceptor;

    // Control interno
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTransferencia estado;

    private LocalDateTime fechaAprobacion;
    private String        usuarioAprobacion;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() { this.creadoEn = LocalDateTime.now(); }

    public enum EstadoTransferencia { PENDIENTE, APROBADO, RECHAZADO }
}
