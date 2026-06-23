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

    private List<MenuNodoDto> hijos = new ArrayList<>();
}
