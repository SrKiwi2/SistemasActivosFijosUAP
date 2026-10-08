package com.usic.SistemasActivosFijosUAP.model.service.seguridad;

import java.util.Set;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** Capacidades de activos que requieren una casilla individual en Permisos de usuario. */
public final class PermisosDatosActivos {

    public static final String VER_FINANZAS = "opcion_activo_ver_finanzas";
    public static final String SUBIR_PENDIENTES_VSIAF = "opcion_activop_subir_vsiaf";

    private PermisosDatosActivos() {}

    public static boolean tiene(HttpServletRequest request, String codigo) {
        if (request == null) return false;
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        Object actor = session.getAttribute("usuario");
        if (!(actor instanceof Usuario usuario) || !"ACTIVO".equals(usuario.getEstado())) return false;
        Object opciones = session.getAttribute("opciones");
        return opciones instanceof Set<?> codigos && codigos.contains(codigo);
    }

    public static boolean puedeVerFinanzas(HttpServletRequest request) {
        // Registro y Pendientes necesitan costo para completar el bien. Los demás
        // módulos lo muestran únicamente si se concede la casilla individual.
        return tiene(request, VER_FINANZAS)
                || tiene(request, "opcion_activo")
                || tiene(request, "opcion_activop");
    }
}
