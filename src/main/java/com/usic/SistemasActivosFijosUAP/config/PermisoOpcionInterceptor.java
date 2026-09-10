package com.usic.SistemasActivosFijosUAP.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 *
 * <p>Segunda vuelta: {@link #RUTAS_EXTRA_POR_OPCION}, para pantallas que llaman a
 * endpoints que viven bajo el prefijo de otro módulo. Sin eso, habilitarle a alguien
 * "Transferencia Interna" obligaba a darle también "Registro de Activos" —y por lo
 * tanto a mostrarle ese módulo en el menú— solo para que el botón de guardar
 * funcionara.
 */
@Component
public class PermisoOpcionInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(PermisoOpcionInterceptor.class);

    /**
     * Rutas que cada opción habilita ADEMÁS de su propia {@code rutaBase}.
     *
     * <p>Hace falta porque muchas pantallas guardan o consultan a través de endpoints
     * que viven bajo el prefijo de OTRO módulo. Como el catálogo asocia una URL a un
     * permiso por prefijo, sin esto la única forma de que una pantalla funcionara
     * completa era darle al usuario el permiso del módulo dueño del prefijo — es
     * decir, mostrarle en el menú un módulo que no le corresponde.
     *
     * <p>La lista salió de un barrido de todas las plantillas contra el catálogo:
     * para cada pantalla, qué URLs {@code /administracion/**} llama y qué permiso las
     * exige hoy. <b>Al agregar una pantalla que llame fuera de su propio prefijo, hay
     * que declararlo acá</b>; si no, sus usuarios verán un 403 al usarla.
     *
     * <p>Un {@code *} representa un segmento variable (un id).
     */
    private static final Map<String, Set<String>> RUTAS_EXTRA_POR_OPCION = Map.ofEntries(
            Map.entry("opcion_trInterna", Set.of(
                    "/administracion/activo/transferencia-masiva",
                    "/administracion/responsable/obtener-siguiente-codigo-funcionario",
                    "/administracion/responsable/api/personas/buscar-por-ci",
                    "/administracion/responsable/registrar-responsable",
                    "/administracion/responsable/registrar-responsable-forzado")),

            Map.entry("opcion_trExterna", Set.of(
                    "/administracion/activo/transferencia-masiva")),

            Map.entry("opcion_consulta_activo", Set.of(
                    "/administracion/activo/datatables")),

            // Asignar Activos: guarda la asignación y puede dar de alta responsables.
            Map.entry("opcion_ActivoAj", Set.of(
                    "/administracion/activo/asignacion-masiva",
                    "/administracion/responsable/obtener-siguiente-codigo-funcionario",
                    "/administracion/responsable/api/personas/buscar-por-ci",
                    "/administracion/responsable/registrar-responsable",
                    "/administracion/responsable/registrar-responsable-forzado")),

            // Asignaciones (seguimiento): abre el detalle y la corrección de un bien.
            Map.entry("opcion_aan", Set.of(
                    "/administracion/activo/api/detalle/*",
                    "/administracion/activo/api/editar-registrado/*")),

            // Registro de Activos: el formulario crea oficina/responsable al vuelo y
            // pide el correlativo.
            Map.entry("opcion_activo", Set.of(
                    "/administracion/correlativo/datos",
                    "/administracion/oficina/registrar-oficina",
                    "/administracion/oficina/siguiente-codigo/*",
                    "/administracion/responsable/obtener-siguiente-codigo-funcionario",
                    "/administracion/responsable/api/personas/buscar-por-ci",
                    "/administracion/responsable/registrar-responsable",
                    "/administracion/responsable/registrar-responsable-forzado")),

            // Registro de Activos Pendientes: casi toda su API vive bajo /activo/api.
            Map.entry("opcion_activop", Set.of(
                    "/administracion/activo/api/agregar-activo-pendiente",
                    "/administracion/activo/api/aprobar-masivo",
                    "/administracion/activo/api/aprobar/*",
                    "/administracion/activo/api/cancelar-pendientes",
                    "/administracion/activo/api/datos-asignacion",
                    "/administracion/activo/api/datos-reporte-pendiente",
                    "/administracion/activo/api/detalle/*",
                    "/administracion/activo/api/editar-lote",
                    "/administracion/activo/asignar-gestion-masiva",
                    "/administracion/activo/generar-correlativo",
                    "/administracion/activo/tabla-registros_pendientes",
                    "/administracion/correlativo/datos-por-codes",
                    "/administracion/responsable-entrega/api/listar")),

            // Faltantes: resolver/reabrir un hallazgo vive bajo el Mapa de Control.
            // El permiso fino (opcion_control_resolver) se sigue validando adentro.
            Map.entry("opcion_control_faltantes", Set.of(
                    "/administracion/control-activos/hallazgos/*")),

            // Revisión de correlativos: registra los huecos como activos.
            Map.entry("opcion_correlativo", Set.of(
                    "/administracion/activo/registrar-huecos-lote")),

            // Reporte de asignaciones: edita los datos del acta que reporta.
            Map.entry("opcion_reporte_asignaciones", Set.of(
                    "/administracion/asignacion/asignaciones/*/editar-reporte")),

            // Responsables: el alta abre el formulario de oficina.
            Map.entry("opcion_responsable", Set.of(
                    "/administracion/oficina/formulario")),

            // Historial de Transferencias: lista y reintenta la sincronización.
            Map.entry("opcion_trHistorial", Set.of(
                    "/administracion/activo/transferencias",
                    "/administracion/activo/transferencias/reintentar-sync")));

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

        if (habilitadaPorRutaExtra(uri, opciones)) {
            return true; // Tiene el permiso de una pantalla que usa este endpoint compartido.
        }

        logger.warn("Acceso denegado por permiso de menú: {}", uri);
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "No tiene permiso para acceder a esta opción.");
        return false;
    }

    /**
     * ¿Alguna de las opciones que tiene el usuario declara esta URL como ruta extra?
     * Se usa como segunda vuelta, después de la coincidencia normal del catálogo.
     */
    private boolean habilitadaPorRutaExtra(String uri, Set<String> opciones) {
        for (String codigo : opciones) {
            Set<String> extras = RUTAS_EXTRA_POR_OPCION.get(codigo);
            if (extras == null) {
                continue;
            }
            for (String patron : extras) {
                if (patronCoincide(uri, patron)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Coincidencia de la URL contra un patrón declarado, donde {@code *} vale por un
     * segmento (un id). El patrón cubre además las sub-rutas: "/a/b" cubre "/a/b/c".
     */
    private boolean patronCoincide(String uri, String patron) {
        if (patron.indexOf('*') < 0) {
            return rutaCoincide(uri, patron);
        }
        String[] segUri = uri.split("/");
        String[] segPat = patron.split("/");
        if (segUri.length < segPat.length) {
            return false;
        }
        for (int i = 0; i < segPat.length; i++) {
            if (!"*".equals(segPat[i]) && !segPat[i].equals(segUri[i])) {
                return false;
            }
        }
        return true;
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
