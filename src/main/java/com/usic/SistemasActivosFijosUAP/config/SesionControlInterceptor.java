package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.usic.SistemasActivosFijosUAP.model.service.seguridad.RecordarmeService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionControlService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Primero de todos los interceptores:
 * <ul>
 *   <li>Si esta sesión se cerró desde otro lado ("Mis sesiones abiertas", el administrador,
 *       cambio de contraseña), la corta ya.</li>
 *   <li>Si no hay sesión pero el navegador trae la cookie de "mantener la sesión iniciada",
 *       la vuelve a armar (cerró el navegador, se reinició el servidor).</li>
 *   <li>Anota el último uso de la sesión (cada 5 min como mucho).</li>
 * </ul>
 */
@Component
public class SesionControlInterceptor implements HandlerInterceptor {

    private final SesionControlService control;
    private final RecordarmeService recordarme;

    public SesionControlInterceptor(SesionControlService control, RecordarmeService recordarme) {
        this.control = control;
        this.recordarme = recordarme;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object id;
            boolean conUsuario;
            try {
                id = session.getAttribute(SesionControlService.ATTR_ID);
                conUsuario = session.getAttribute("usuario") != null;
            } catch (IllegalStateException sesionYaInvalida) {
                return true;
            }
            if (id instanceof Long idSesion) {
                if (control.cerradaADistancia(idSesion)) {
                    return cortar(request, response, session);
                }
                if (!recordarme.tocar(request, response, session)) {
                    return cortar(request, response, session); // cerrada en la base (p. ej. antes de un reinicio)
                }
                return true;
            }
            if (conUsuario) {
                return true; // sesión de antes de este control: sigue hasta que venza
            }
        }
        if (recordarme.traeCookie(request)) {
            recordarme.restaurar(request, response);
        }
        return true;
    }

    private boolean cortar(HttpServletRequest request, HttpServletResponse response, HttpSession session)
            throws Exception {
        try {
            session.setAttribute(SesionControlService.ATTR_MOTIVO, "CERRADA_A_DISTANCIA");
            session.invalidate();
        } catch (IllegalStateException yaInvalida) {
            // nada
        }
        recordarme.olvidar(request, response);
        if (response.isCommitted()) {
            return false;
        }
        if (SesionInactividadInterceptor.esNavegacion(request)) {
            response.sendRedirect("/?sesion=revocada");
        } else {
            response.setHeader(SesionInactividadInterceptor.HEADER_MOTIVO, "revocada");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sesión cerrada desde otro equipo");
        }
        return false;
    }
}
