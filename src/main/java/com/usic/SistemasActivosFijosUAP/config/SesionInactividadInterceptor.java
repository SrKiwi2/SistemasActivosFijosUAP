package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionControlService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionInactividadService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Cierra la sesión que pasó el límite de inactividad (ver {@link SesionInactividadService})
 * y cuenta como actividad las navegaciones de la persona (abrir una pantalla).
 *
 * <p>Va PRIMERO: una sesión vencida no debe llegar a actualizar permisos ni a atender
 * nada. Una petición AJAX recibe 401 con {@code X-Sciaf-Sesion: inactividad} (el
 * navegador muestra "Su sesión se cerró por inactividad", no el aviso genérico); una
 * navegación vuelve al inicio de sesión con el motivo en la URL.
 */
@Component
public class SesionInactividadInterceptor implements HandlerInterceptor {

    public static final String HEADER_MOTIVO = "X-Sciaf-Sesion";

    private final SesionInactividadService inactividad;

    public SesionInactividadInterceptor(SesionInactividadService inactividad) {
        this.inactividad = inactividad;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return true;
        }
        try {
            if (session.getAttribute("usuario") == null) {
                return true; // sin login: no hay nada que vencer
            }
            if (!inactividad.vencida(session)) {
                if (esNavegacion(request)) {
                    inactividad.marcar(session, System.currentTimeMillis());
                }
                return true;
            }
            session.setAttribute(SesionControlService.ATTR_MOTIVO, "INACTIVIDAD");
            session.invalidate();
        } catch (IllegalStateException sesionYaInvalida) {
            return true;
        }

        if (response.isCommitted()) {
            return false;
        }
        if (esNavegacion(request)) {
            response.sendRedirect("/?sesion=inactividad");
        } else {
            response.setHeader(HEADER_MOTIVO, "inactividad");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sesión cerrada por inactividad");
        }
        return false;
    }

    /**
     * La persona abrió una pantalla (no es un fetch/AJAX). Los navegadores actuales mandan
     * Sec-Fetch-Mode; para los que no, se mira que pida HTML y no sea XMLHttpRequest.
     */
    static boolean esNavegacion(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String modo = request.getHeader("Sec-Fetch-Mode");
        if (modo != null) {
            return "navigate".equals(modo);
        }
        String accept = request.getHeader("Accept");
        return !"XMLHttpRequest".equals(request.getHeader("X-Requested-With"))
                && accept != null && accept.contains("text/html");
    }
}
