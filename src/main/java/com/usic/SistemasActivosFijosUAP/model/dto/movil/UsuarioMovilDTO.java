package com.usic.SistemasActivosFijosUAP.model.dto.movil;

import java.util.Set;

/**
 * Perfil que la app móvil necesita para pintar la interfaz.
 *
 * <p>{@code permisos} son los códigos de {@code opcion_menu} vigentes para el
 * usuario (los {@code MOV_*} y los de la web). La app los usa <b>solo</b> para
 * mostrar u ocultar opciones: cada endpoint vuelve a comprobarlos en el
 * servidor.
 */
public record UsuarioMovilDTO(
        Long        idUsuario,
        String      usuario,
        String      nombreCompleto,
        String      rol,
        Set<String> permisos
) {}
