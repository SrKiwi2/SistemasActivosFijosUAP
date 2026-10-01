package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Auditoría de los permisos de menú de cada usuario: qué se le agregó, qué se le quitó,
 * quién, cuándo, desde qué IP y desde qué pantalla.
 *
 * <p>También guarda el <b>respaldo</b> de los permisos que tenía cuando se lo dejó sin
 * acceso (desactivado o con todos los permisos quitados): al activarlo de nuevo se
 * restauran desde acá. Respaldo vacío ("") = usaba la plantilla de su rol; null = la
 * fila no es un respaldo.
 *
 * <p>Los códigos se guardan tal cual (opcion_xxx, separados por coma) y los nombres se
 * resuelven al mostrar, así se sigue leyendo aunque la opción después se renombre o se
 * elimine del menú. La tabla la crea scripts/sql/historial_permiso_usuario.sql.
 */
@Entity
@Table(name = "historial_permiso_usuario", indexes = {
        @Index(name = "idx_hpu_usuario", columnList = "id_usuario"),
        @Index(name = "idx_hpu_fecha", columnList = "fecha")
})
@Getter
@Setter
public class HistorialPermisoUsuario {

    public static final String ASIGNACION = "ASIGNACION";
    public static final String PLANTILLA_ROL = "PLANTILLA_ROL";
    public static final String SIN_ACCESO = "SIN_ACCESO";
    public static final String DESACTIVAR = "DESACTIVAR";
    public static final String ACTIVAR = "ACTIVAR";
    public static final String CAMBIO_ROL = "CAMBIO_ROL";
    public static final String ELIMINAR = "ELIMINAR";
    public static final String QUITADO_POR_MENU = "QUITADO_POR_MENU";
    public static final String MIGRACION = "MIGRACION";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_historial")
    private Long idHistorial;

    /** Usuario afectado. */
    @Column(name = "id_usuario", nullable = false)
    private Long idUsuario;

    @Column(name = "usuario", length = 60)
    private String usuario;

    @Column(name = "accion", length = 30, nullable = false)
    private String accion;

    /** Pantalla o proceso desde donde se hizo, ej. "Usuarios › Permisos de menú". */
    @Column(name = "origen", length = 80)
    private String origen;

    @Column(name = "agregados", columnDefinition = "text")
    private String agregados;

    @Column(name = "quitados", columnDefinition = "text")
    private String quitados;

    /** Permisos propios que tenía antes de dejarlo sin acceso (ver javadoc de la clase). */
    @Column(name = "respaldo", columnDefinition = "text")
    private String respaldo;

    @Column(name = "cantidad_antes")
    private Integer cantidadAntes;

    @Column(name = "cantidad_despues")
    private Integer cantidadDespues;

    @Column(name = "detalle", columnDefinition = "text")
    private String detalle;

    /** Quién lo hizo (null = el sistema). */
    @Column(name = "id_actor")
    private Long idActor;

    @Column(name = "actor", length = 60)
    private String actor;

    @Column(name = "rol_actor", length = 40)
    private String rolActor;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
}
