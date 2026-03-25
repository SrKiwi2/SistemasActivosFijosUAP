package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "historial_activo")
@Getter @Setter @NoArgsConstructor
public class HistorialActivo {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_activo", nullable = false)
    private Activo activo;
 
    /** Snapshot del código para búsquedas históricas */
    @Column(name = "codigo_activo", length = 50, nullable = false)
    private String codigoActivo;
 
    /**
     * REGISTRO | MODIFICACION | TRANSFERENCIA_INT | TRANSFERENCIA_EXT |
     * ASIGNACION | DESASIGNACION | BAJA | REACTIVACION | DEPRECIACION
     */
    @Column(name = "tipo_evento", length = 50, nullable = false)
    private String tipoEvento;
 
    /** Referencia a la transferencia si el evento es de ese tipo */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_transferencia")
    private Transferencia transferencia;
 
    /* ── Estado ANTES del evento ── */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_anterior")
    private Oficina oficinaAnterior;
 
    @Column(name = "nombre_oficina_anterior", length = 200)
    private String nombreOficinaAnterior;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resp_anterior")
    private Responsable responsableAnterior;
 
    @Column(name = "nombre_resp_anterior", length = 200)
    private String nombreRespAnterior;
 
    /* ── Estado DESPUÉS del evento ── */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_oficina_nueva")
    private Oficina oficinaNueva;
 
    @Column(name = "nombre_oficina_nueva", length = 200)
    private String nombreOficinaNueva;
 
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_resp_nuevo")
    private Responsable responsableNuevo;
 
    @Column(name = "nombre_resp_nuevo", length = 200)
    private String nombreRespNuevo;
 
    /** Descripción generada por el sistema: "Transferido de X a Y por usuario Z" */
    @Column(name = "descripcion_evento", columnDefinition = "TEXT")
    private String descripcionEvento;
 
    @Column(name = "fecha_evento", nullable = false)
    private LocalDateTime fechaEvento;
 
    @Column(name = "id_usuario")
    private Long idUsuario;
 
    @Column(name = "nombre_usuario", length = 100)
    private String nombreUsuario;
 
    /* ── Builder estático de conveniencia ── */
    public static HistorialActivo crear(
            Activo activo,
            String tipoEvento,
            Oficina ofAnterior, Responsable respAnterior,
            Oficina ofNueva,    Responsable respNuevo,
            Transferencia transferencia,
            String descripcion,
            Long idUsuario, String nombreUsuario) {
 
        HistorialActivo h = new HistorialActivo();
        h.activo          = activo;
        h.codigoActivo    = activo.getCodigo();
        h.tipoEvento      = tipoEvento;
        h.transferencia   = transferencia;
        h.fechaEvento     = LocalDateTime.now();
        h.idUsuario       = idUsuario;
        h.nombreUsuario   = nombreUsuario;
        h.descripcionEvento = descripcion;
 
        if (ofAnterior != null) {
            h.oficinaAnterior      = ofAnterior;
            h.nombreOficinaAnterior = ofAnterior.getNombre();
        }
        if (respAnterior != null) {
            h.responsableAnterior  = respAnterior;
            h.nombreRespAnterior   = respAnterior.getPersona().getNombreCompleto();
        }
        if (ofNueva != null) {
            h.oficinaNueva      = ofNueva;
            h.nombreOficinaNueva = ofNueva.getNombre();
        }
        if (respNuevo != null) {
            h.responsableNuevo  = respNuevo;
            h.nombreRespNuevo   = respNuevo.getPersona().getNombreCompleto();
        }
        return h;
    }

}
