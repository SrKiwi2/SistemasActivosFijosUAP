package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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

/**
 * Agregado "envío" de un comunicado emitido por un administrador/responsable.
 *
 * Un {@code Comunicado} representa lo que se mandó y a quién (alcance), mientras
 * que cada destinatario concreto es una fila {@link Notificacion} enlazada a este
 * comunicado vía {@code id_comunicado}. Esto permite el panel de control de
 * lecturas: quién recibió y quién confirmó la lectura.
 *
 * Las notificaciones generadas por el sistema (p.ej. transferencias) dejan
 * {@code comunicado = null} y no crean un registro aquí.
 */
@Entity
@Table(
    name = "comunicado",
    indexes = {
        @Index(name = "idx_com_emisor", columnList = "id_emisor"),
        @Index(name = "idx_com_fecha",  columnList = "fecha_envio")
    }
)
@Getter @Setter
public class Comunicado extends AuditoriaConfig {

    private static final long serialVersionUID = 2629195288020321925L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idComunicado;

    // Usuario que emite el comunicado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_emisor", nullable = false,
                foreignKey = @ForeignKey(name = "fk_com_emisor"))
    private Usuario emisor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 50)
    private Notificacion.TipoNotificacion tipo;

    @Size(max = 255)
    @Column(name = "titulo", nullable = false, length = 255)
    private String titulo;

    @Column(name = "mensaje", columnDefinition = "text")
    private String mensaje;

    @Size(max = 512)
    @Column(name = "url_destino", length = 512)
    private String urlDestino;

    // Marca el comunicado como destacado/urgente (solo afecta presentación)
    @Column(name = "importante", nullable = false)
    private boolean importante = false;

    // Cómo se resolvió la audiencia
    @Enumerated(EnumType.STRING)
    @Column(name = "alcance", nullable = false, length = 20)
    private Alcance alcance;

    // Texto legible del alcance (ej: "Todos (12)", "Oficina: Almacén Central")
    @Size(max = 255)
    @Column(name = "alcance_detalle", length = 255)
    private String alcanceDetalle;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio = LocalDateTime.now();

    // Total de destinatarios resueltos al momento del envío (denormalizado)
    @Column(name = "total_destinatarios", nullable = false)
    private int totalDestinatarios = 0;

    public enum Alcance {
        TODOS,
        USUARIOS,
        OFICINA
    }
}
