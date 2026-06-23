package com.usic.SistemasActivosFijosUAP.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Bloqueo de acceso por permiso de menú (Fase 3).
 *
 * Para cada petición bajo {@code /administracion/**} busca las opciones del
 * catálogo cuya {@code rutaBase} coincide con la URL (por segmentos de ruta) y
 * exige que {@code session.opciones} contenga al menos una de ellas. Si la URL
 * no está mapeada a ninguna opción (sub-endpoints AJAX no representados en el
 * menú) o la sesión no trae permisos, se deja pasar (fail-open) para no romper
 * el flujo ni sesiones previas a la funcionalidad. ADMINISTRADOR pasa siempre
 * porque su {@code session.opciones} incluye todos los códigos.
 */
@Component
public class PermisoOpcionInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(PermisoOpcionInterceptor.class);

    private final IOpcionMenuService opcionMenuService;

    public PermisoOpcionInterceptor(IOpcionMenuService opcionMenuService) {
        this.opcionMenuService = opcionMenuService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/administracion/")) {
            return true;
        }

        // Opciones del catálogo cuya rutaBase cubre esta URL.
        List<OpcionMenu> coincidencias = new ArrayList<>();
        for (OpcionMenu opcion : getCatalogo()) {
            if (rutaCoincide(uri, opcion.getRutaBase())) {
                coincidencias.add(opcion);
            }
        }
        if (coincidencias.isEmpty()) {
            return true; // URL no asociada a un ítem de menú → no se controla aquí.
        }

        Set<String> opciones = obtenerOpcionesSesion(request);
        if (opciones == null) {
            return true; // Sin info de permisos en sesión → no bloquear (auth se valida aparte).
        }

        for (OpcionMenu opcion : coincidencias) {
            if (opciones.contains(opcion.getCodigo())) {
                return true; // Tiene al menos uno de los permisos requeridos.
            }
        }

        logger.warn("Acceso denegado por permiso de menú: {}", uri);
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "No tiene permiso para acceder a esta opción.");
        return false;
    }

    /** Coincidencia por segmentos: evita que "/a/vista" matchee "/a/vistap". */
    private boolean rutaCoincide(String uri, String rutaBase) {
        if (rutaBase == null || rutaBase.isEmpty()) {
            return false;
        }
        return uri.equals(rutaBase) || uri.startsWith(rutaBase + "/");
    }

    @SuppressWarnings("unchecked")
    private Set<String> obtenerOpcionesSesion(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object attr = session.getAttribute("opciones");
        return (attr instanceof Set) ? (Set<String>) attr : null;
    }

    private List<OpcionMenu> getCatalogo() {
        // listarItems() ya viene cacheado en el servicio y se invalida al editar
        // el catálogo (limpiarCacheMenu). Solo los ítems hoja tienen rutaBase.
        return opcionMenuService.listarItems();
    }
}
