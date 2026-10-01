package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Nodo del árbol de menú entregado al sidebar y a la pantalla de gestión, ya
 * desacoplado de la entidad (evita lazy-loading en la plantilla).
 * SECCION → GRUPO → ITEM via {@code hijos}.
 */
@Getter
@Setter
public class MenuNodoDto {

    private Long idOpcion;
    private String codigo;
    private String descripcion;
    private String tipo;
    private String icono;
    private String colorClase;
    private String badge;
    private String url;
    private String rutaBase;
    private Integer orden;
    private Boolean visible;

    /** ACTIVO / BLOQUEADO (los eliminados no llegan al árbol). */
    private String estado;
    /** Bloqueado por sí mismo o porque su grupo/sección está bloqueado. */
    private boolean bloqueado;
    /** No se puede ocultar, bloquear ni eliminar (dejaría al administrador sin acceso). */
    private boolean protegido;
    /** Ítem sin URL: es un permiso puro (capacidad), no una pantalla. */
    private boolean permisoPuro;

    // Solo para la pantalla de gestión:
    /** Usuarios con este ítem asignado explícitamente. */
    private long usuariosAsignados;
    /** La URL apunta a una ruta que existe en el sistema (null = no se revisó). */
    private Boolean urlExiste;

    private List<MenuNodoDto> hijos = new ArrayList<>();
}
