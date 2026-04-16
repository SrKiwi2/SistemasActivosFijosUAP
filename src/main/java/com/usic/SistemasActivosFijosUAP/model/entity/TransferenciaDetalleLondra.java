package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.ForeignKey;

@Entity
@Table(name = "transferencia_detalle_londra")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferenciaDetalleLondra {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cabecera",
                nullable = false,
                foreignKey = @ForeignKey(name = "fk_detalle_cabecera"))
    private TransferenciaCabecera cabecera;

    // Datos específicos de ESTE activo
    private Long  idTDbf;
    private String codigoActivo;
    private Short  codContO;
    private Short  codAuxO;
    private Short  estadoActivoO;
    private Short  codOficO;       // oficina específica del responsable de este activo
    private Short  codRespO;       // responsable específico de este activo

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoActivo estadoDetalle;

    @Column(updatable = false)
    private LocalDateTime creadoEn;

    @PrePersist
    protected void onCreate() { this.creadoEn = LocalDateTime.now(); }

    public enum EstadoActivo { APROBADO, ERROR }
}
