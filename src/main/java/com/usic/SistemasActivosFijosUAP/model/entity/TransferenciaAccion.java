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
import jakarta.persistence.Index;
import jakarta.persistence.ForeignKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "transferencia_accion",
    indexes = {
        @Index(name = "idx_trf_accion_cabecera", columnList = "id_cabecera"),
        @Index(name = "idx_trf_accion_fecha",    columnList = "fecha_accion"),
        @Index(name = "idx_trf_accion_tipo",     columnList = "tipo_accion")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransferenciaAccion {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idAccion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cabecera", nullable = false,
                foreignKey = @ForeignKey(name = "fk_accion_cabecera"))
    private TransferenciaCabecera cabecera;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_accion", nullable = false, length = 20)
    private TipoAccion tipoAccion;

    @Column(columnDefinition = "text")
    private String motivo;           // obligatorio para RECHAZADO y OBSERVADO

    @Column(name = "usuario_accion", length = 100, nullable = false)
    private String usuarioAccion;

    @Column(name = "fecha_accion", nullable = false)
    private LocalDateTime fechaAccion;

    // Resultado del callback a Londra para esta acción
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_callback", length = 20)
    private TransferenciaCabecera.EstadoCallback estadoCallback;

    @Column(name = "respuesta_callback", columnDefinition = "text")
    private String respuestaCallback;

    @PrePersist
    protected void onCreate() {
        if (this.fechaAccion == null)
            this.fechaAccion = LocalDateTime.now();
    }

    public enum TipoAccion {
        FINALIZADO,   // aprobado completamente
        RECHAZADO,    // rechazado con motivo
        OBSERVADO,    // observado — puede volver a procesarse
        CANCELADO     // cancelado
    }

}
