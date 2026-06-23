package com.usic.SistemasActivosFijosUAP.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.usic.SistemasActivosFijosUAP.config.AuditoriaConfig;

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
import lombok.Setter;

/**
 * Catálogo de las opciones del menú lateral, modelado como árbol.
 *
 * Cada fila es un nodo de uno de tres {@code tipo}:
 *   · SECCION → encabezado de sección (solo texto), p. ej. "Catálogos".
 *   · GRUPO   → menú desplegable con ícono, p. ej. "Usuarios y Accesos".
 *   · ITEM    → opción navegable (hoja) con URL y permiso, p. ej. "Rol".
 *
 * Los ITEM conservan el {@code codigo} estable ("opcion_xxx") que usan
 * usuario_opcion, session.opciones y el diccionario rutasMenu del frontend.
 * La jerarquía se expresa con {@code padre} (ITEM→GRUPO→SECCION).
 */
@Entity
@Table(name = "opcion_menu")
@Setter
@Getter
public class OpcionMenu extends AuditoriaConfig {

    private static final long serialVersionUID = 2629195288020321925L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idOpcion;

    /** Llave estable del nodo, ej. "opcion_rol" (ITEM) o "sec_admin" (SECCION). */
    @Column(unique = true, nullable = false)
    private String codigo;

    /** Texto visible en el menú / checkbox de asignación, ej. "Rol". */
    private String descripcion;

    /** Tipo de nodo: SECCION | GRUPO | ITEM. */
    private String tipo;

    /** Encabezado de sección (denormalizado, usado por la pantalla de permisos). */
    private String seccion;

    /** Grupo padre (denormalizado, usado por la pantalla de permisos). */
    private String grupo;

    /** Clase de ícono Tabler, ej. "ti ti-users-group" (GRUPO/ITEM). */
    private String icono;

    /** Sufijo de color del tema, ej. "purple" → gi-purple / si-purple. */
    private String colorClase;

    /** Texto de insignia opcional, ej. "PEND." (ITEM). */
    private String badge;

    /** URL exacta de la vista (igual al diccionario rutasMenu de script.html). */
    private String url;

    /** Prefijo de URL usado por el interceptor de enforcement. */
    private String rutaBase;

    /** Orden de despliegue entre hermanos del mismo padre. */
    private Integer orden;

    /** Si está oculto, no se renderiza en el sidebar (sin borrarlo). */
    private Boolean visible;

    /** Nodo padre en el árbol (ITEM→GRUPO, GRUPO→SECCION, SECCION→null). */
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_padre")
    private OpcionMenu padre;
}
