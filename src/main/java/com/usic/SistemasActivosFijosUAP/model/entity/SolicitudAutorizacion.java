package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

/**
 * Pedido de un usuario sin rol administrativo para hacer una operación de alto impacto
 * (eliminar, o cambiar la clave de una oficina / responsable). No se aplica nada hasta
 * que el revisor la aprueba; recién ahí se ejecuta con los datos guardados en
 * {@link #datosJson} y se envía al VSIAF.
 */
@Entity
@Table(name = "solicitud_autorizacion", indexes = {
    @Index(name = "ix_solicitud_estado", columnList = "estado"),
    @Index(name = "ix_solicitud_registro", columnList = "tipo, id_registro")
})
@Getter @Setter
public class SolicitudAutorizacion {

    public static final String PENDIENTE = "PENDIENTE";
    public static final String APROBADA = "APROBADA";
    public static final String RECHAZADA = "RECHAZADA";
    /** Se aprobó pero al ejecutarla falló (p. ej. la oficina ya tenía responsables). */
    public static final String FALLIDA = "FALLIDA";
    /** El propio solicitante la retiró. */
    public static final String ANULADA = "ANULADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_solicitud")
    private Long idSolicitud;

    /** Evita que dos revisores la resuelvan a la vez. */
    @Version
    @Column(name = "version")
    private Long version;

    /** OFICINA_MODIFICAR, OFICINA_ELIMINAR, RESP_MODIFICAR, RESP_ELIMINAR. */
    @Column(name = "tipo", length = 40, nullable = false)
    private String tipo;

    @Column(name = "modulo", length = 30, nullable = false)
    private String modulo;

    @Column(name = "id_registro", nullable = false)
    private Long idRegistro;

    /** Código visible del registro (p. ej. "CAUN-5 SISTEMAS"). */
    @Column(name = "referencia", length = 200)
    private String referencia;

    /** Qué cambia, legible: "Código: 5 → 7; Predio: CAUN → CULP". */
    @Column(name = "resumen", columnDefinition = "text")
    private String resumen;

    /** Datos para ejecutar la operación al aprobar. */
    @Column(name = "datos_json", columnDefinition = "text")
    private String datosJson;

    @Column(name = "motivo", columnDefinition = "text")
    private String motivo;

    @Column(name = "estado", length = 20, nullable = false)
    private String estado = PENDIENTE;

    @Column(name = "id_solicitante", nullable = false)
    private Long idSolicitante;

    @Column(name = "solicitante", length = 60)
    private String solicitante;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    @Column(name = "id_revisor")
    private Long idRevisor;

    @Column(name = "revisor", length = 60)
    private String revisor;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    /** Comentario del revisor, o el resultado / error de ejecutarla. */
    @Column(name = "respuesta", columnDefinition = "text")
    private String respuesta;
}
