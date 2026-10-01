package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionPermisosService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionPermisosService.Revision;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Antes de atender cualquier petición con sesión, la pone al día con los permisos
 * vigentes (ver {@link SesionPermisosService}). Va ANTES que
 * {@link PermisoOpcionInterceptor}: así un permiso recién quitado se respeta en la misma
 * petición, sin esperar a que el usuario vuelva a iniciar sesión.
 *
 * <p>Si el usuario fue desactivado, eliminado o le cerraron las sesiones, la sesión se
 * invalida acá: una petición AJAX recibe 401 (la pantalla ya sabe mostrar "Sesión
 * expirada") y una navegación normal vuelve al inicio de sesión.
 */
@Component
public class SesionPermisosInterceptor implements HandlerInterceptor {

    private final SesionPermisosService sesionPermisos;

    public SesionPermisosInterceptor(SesionPermisosService sesionPermisos) {
        this.sesionPermisos = sesionPermisos;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }
        Revision r;
        try {
            r = sesionPermisos.revisar(session);
        } catch (IllegalStateException sesionYaInvalida) {
            return true;
        }
        if (r != Revision.CERRADA) {
            return true;
        }

        session.invalidate();
        if (response.isCommitted()) {
            return false;
        }
        boolean ajax = "XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));
        if (ajax) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sesión cerrada por el administrador");
        } else {
            response.sendRedirect("/");
        }
        return false;
    }
}
