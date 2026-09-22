package com.usic.SistemasActivosFijosUAP.config;

import java.util.Set;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Roles con capacidad administrativa: aplican directo las operaciones de alto impacto
 * (el resto pide autorización), ven la auditoría y el Monitoreo de actividad, y reciben
 * los avisos de actividad. El chequeo se hace en el servidor: en este proyecto casi
 * todas las rutas son permitAll().
 */
public final class RolesSciaf {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";
    public static final String SUPER_USUARIO = "SUPER USUARIO";
    public static final Set<String> ADMINISTRATIVOS = Set.of(ADMINISTRADOR, SUPER_USUARIO);

    private RolesSciaf() {}

    public static String rolDe(Usuario u) {
        return (u != null && u.getRol() != null && u.getRol().getNombre() != null)
                ? u.getRol().getNombre().trim().toUpperCase() : "";
    }

    public static boolean esAdministrativo(Usuario u) {
        return ADMINISTRATIVOS.contains(rolDe(u));
    }

    public static boolean esAdministrador(Usuario u) {
        return ADMINISTRADOR.equals(rolDe(u));
    }

    public static Usuario usuarioDe(HttpServletRequest request) {
        if (request == null || request.getSession(false) == null) return null;
        Object u = request.getSession().getAttribute("usuario");
        return (u instanceof Usuario usuario) ? usuario : null;
    }

    public static boolean esAdministrativo(HttpServletRequest request) {
        return esAdministrativo(usuarioDe(request));
    }
}
