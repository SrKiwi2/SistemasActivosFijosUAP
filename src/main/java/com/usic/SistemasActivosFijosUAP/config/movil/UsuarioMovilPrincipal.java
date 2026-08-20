package com.usic.SistemasActivosFijosUAP.config.movil;

import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Identidad del usuario móvil dentro de una petición autenticada.
 *
 * <p>Lo deja {@link JwtAuthFilter} en el {@code SecurityContext}. Los
 * controladores de {@code /api/movil/**} lo recuperan con {@link #actual()} en
 * lugar de leer la sesión HTTP (que en móvil no existe).
 */
public record UsuarioMovilPrincipal(
        Long        idUsuario,
        String      usuario,
        String      rol,
        Set<String> permisos
) {

    /** Rol con el formato que usa Spring Security ("SUPER USUARIO" → "SUPER_USUARIO"). */
    public String rolNormalizado() {
        return rol == null ? "" : rol.trim().toUpperCase().replace(' ', '_');
    }

    public boolean tienePermiso(String codigo) {
        return permisos != null && permisos.contains(codigo);
    }

    public boolean esAdministrador() {
        String r = rolNormalizado();
        return "ADMINISTRADOR".equals(r) || "SUPER_USUARIO".equals(r);
    }

    /**
     * Usuario móvil de la petición en curso.
     *
     * @return el principal, o {@code null} si la petición no está autenticada
     *         por JWT (por ejemplo, si llega por la cadena de seguridad web).
     */
    public static UsuarioMovilPrincipal actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioMovilPrincipal p) {
            return p;
        }
        return null;
    }
}
