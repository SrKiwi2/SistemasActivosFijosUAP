package com.usic.SistemasActivosFijosUAP.controller.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.dto.MenuNodoDto;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionPermisosService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Gestión del menú (catálogo opcion_menu): crear, editar, ordenar (arrastrando), mover
 * entre grupos, mostrar/ocultar, bloquear, eliminar y restaurar secciones, grupos e
 * ítems del sidebar.
 *
 * <p>Cada cambio se avisa a todos los usuarios conectados (SSE "menu"): su menú se
 * actualiza sin recargar la página ni volver a iniciar sesión. Y queda en el monitoreo
 * de actividad.
 *
 * <p>Acceso: quien tenga el ítem "Gestión de Menú" (lo controla PermisoOpcionInterceptor
 * por rutaBase /administracion/menu) o sea ADMINISTRADOR; se vuelve a revisar acá porque
 * el interceptor deja pasar las sesiones sin permisos cargados.
 */
@Controller
@RequestMapping("/administracion/menu")
public class OpcionMenuController {

    private static final String CODIGO_GESTION = "opcion_menu_admin";

    private final IOpcionMenuService opcionMenuService;
    private final SesionPermisosService sesionPermisos;
    private final ActividadService actividadService;
    private final RequestMappingHandlerMapping handlerMapping;

    public OpcionMenuController(IOpcionMenuService opcionMenuService, SesionPermisosService sesionPermisos,
            ActividadService actividadService,
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.opcionMenuService = opcionMenuService;
        this.sesionPermisos = sesionPermisos;
        this.actividadService = actividadService;
        this.handlerMapping = handlerMapping;
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista() {
        return "menu/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla")
    public String tabla(Model model) {
        List<MenuNodoDto> arbol = opcionMenuService.obtenerArbolAdmin();
        Set<String> rutas = rutasGet();
        marcarUrls(arbol, rutas);
        model.addAttribute("arbol", arbol);
        model.addAttribute("eliminados", opcionMenuService.listarEliminados());
        return "menu/tabla";
    }

    private void marcarUrls(List<MenuNodoDto> nodos, Set<String> rutas) {
        for (MenuNodoDto n : nodos) {
            if (n.getUrl() != null && !n.getUrl().isBlank()) {
                n.setUrlExiste(rutas.contains(n.getUrl().trim()));
            }
            marcarUrls(n.getHijos(), rutas);
        }
    }

    /**
     * Rutas GET que existen en el sistema y devuelven una pantalla (todo lo de
     * /administracion/** y /reportes/** que se puede abrir). Alimentan el autocompletado
     * de la URL en el formulario y la marca de "URL inexistente" en el árbol.
     */
    private Set<String> rutasGet() {
        Set<String> rutas = new TreeSet<>();
        handlerMapping.getHandlerMethods().forEach((info, metodo) -> {
            Set<RequestMethod> metodos = info.getMethodsCondition().getMethods();
            if (!metodos.isEmpty() && !metodos.contains(RequestMethod.GET)) return;
            for (String patron : info.getPatternValues()) {
                if (patron.contains("{")) continue;
                if (patron.startsWith("/administracion/") || patron.startsWith("/reportes/")) {
                    rutas.add(patron);
                }
            }
        });
        return rutas;
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/rutas")
    @ResponseBody
    public List<String> rutas() {
        // Primero las que parecen pantallas (terminan en /vista o /modulo).
        List<String> vistas = new ArrayList<>();
        List<String> otras = new ArrayList<>();
        for (String r : rutasGet()) {
            if (r.endsWith("/vista") || r.endsWith("/modulo") || r.endsWith("/vistap")) vistas.add(r); else otras.add(r);
        }
        vistas.addAll(otras);
        return vistas;
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, @RequestParam(value = "tipo", required = false) String tipo,
            @RequestParam(value = "idPadre", required = false) Long idPadre) {
        OpcionMenu nodo = new OpcionMenu();
        nodo.setTipo(tipo != null ? tipo : "ITEM");
        model.addAttribute("nodo", nodo);
        model.addAttribute("edit", false);
        model.addAttribute("idPadreActual", idPadre);
        model.addAttribute("secciones", opcionMenuService.listarSecciones());
        model.addAttribute("grupos", opcionMenuService.listarGrupos());
        return "menu/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id}")
    public String formularioEdit(Model model, @PathVariable("id") Long id) {
        OpcionMenu nodo = opcionMenuService.findById(id);
        Long idPadreActual = (nodo != null && nodo.getPadre() != null) ? nodo.getPadre().getIdOpcion() : null;

        model.addAttribute("nodo", nodo);
        model.addAttribute("edit", true);
        model.addAttribute("idPadreActual", idPadreActual);
        model.addAttribute("secciones", opcionMenuService.listarSecciones());
        model.addAttribute("grupos", opcionMenuService.listarGrupos());
        return "menu/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/guardar")
    public ResponseEntity<Map<String, Object>> guardar(
            HttpServletRequest request,
            @RequestParam(value = "idOpcion", required = false) Long idOpcion,
            @RequestParam("codigo") String codigo,
            @RequestParam("descripcion") String descripcion,
            @RequestParam("tipo") String tipo,
            @RequestParam(value = "idPadre", required = false) Long idPadre,
            @RequestParam(value = "icono", required = false) String icono,
            @RequestParam(value = "colorClase", required = false) String colorClase,
            @RequestParam(value = "badge", required = false) String badge,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "rutaBase", required = false) String rutaBase,
            @RequestParam(value = "visible", defaultValue = "false") boolean visible) {

        Map<String, Object> response = new HashMap<>();
        ResponseEntity<Map<String, Object>> sinAcceso = exigirAcceso(request);
        if (sinAcceso != null) return sinAcceso;

        try {
            String codigoNormalizado = (codigo != null) ? codigo.trim() : "";
            String descripcionNormalizada = (descripcion != null) ? descripcion.trim() : "";
            if (codigoNormalizado.isEmpty() || descripcionNormalizada.isEmpty()) {
                response.put("ok", false);
                response.put("msg", "El código y la descripción son obligatorios.");
                return ResponseEntity.ok(response);
            }
            if (!codigoNormalizado.matches("[A-Za-z0-9_]+")) {
                response.put("ok", false);
                response.put("msg", "El código solo puede tener letras, números y guion bajo (ej. opcion_mi_modulo).");
                return ResponseEntity.ok(response);
            }

            // Validar unicidad del código (también contra los eliminados: siguen ocupándolo).
            OpcionMenu porCodigo = opcionMenuService.listarTodas().stream()
                    .filter(o -> codigoNormalizado.equals(o.getCodigo()))
                    .findFirst().orElse(null);
            if (porCodigo != null && (idOpcion == null || !porCodigo.getIdOpcion().equals(idOpcion))) {
                response.put("ok", false);
                response.put("msg", "Ya existe una opción con el código '" + codigoNormalizado + "'"
                        + (porCodigo.eliminado() ? " (está en la papelera: puede restaurarla)." : "."));
                return ResponseEntity.ok(response);
            }

            String urlN = vacioANulo(url);
            String rutaBaseN = vacioANulo(rutaBase);

            // ITEM: con URL es una pantalla; sin URL, un permiso puro (casilla de permiso
            // que habilita una acción, no aparece en el menú).
            if ("ITEM".equals(tipo)) {
                if (idPadre == null) {
                    response.put("ok", false);
                    response.put("msg", "Un ítem debe pertenecer a un grupo.");
                    return ResponseEntity.ok(response);
                }
                if (urlN != null) {
                    if (!urlN.startsWith("/")) {
                        response.put("ok", false);
                        response.put("msg", "La URL tiene que empezar con «/», ej. /administracion/mi-modulo/vista.");
                        return ResponseEntity.ok(response);
                    }
                    // Si no se indicó la ruta base, se deduce de la URL (sin el /vista final).
                    if (rutaBaseN == null) {
                        rutaBaseN = urlN.replaceAll("/(vista|modulo)$", "");
                    }
                }
            }
            if ("GRUPO".equals(tipo) && idPadre == null) {
                response.put("ok", false);
                response.put("msg", "Un grupo debe pertenecer a una sección.");
                return ResponseEntity.ok(response);
            }

            OpcionMenu nodo = (idOpcion != null) ? opcionMenuService.findById(idOpcion) : new OpcionMenu();
            if (nodo == null || nodo.eliminado()) {
                response.put("ok", false);
                response.put("msg", "La opción a modificar no existe.");
                return ResponseEntity.ok(response);
            }
            if (idOpcion != null && !tipo.equals(nodo.getTipo())) {
                response.put("ok", false);
                response.put("msg", "No se puede cambiar el tipo de una opción existente. Cree una nueva.");
                return ResponseEntity.ok(response);
            }
            if (idOpcion != null && !visible && Boolean.TRUE.equals(nodo.getVisible())
                    && nodo.getUrl() != null && esProtegida(nodo)) {
                response.put("ok", false);
                response.put("msg", "Esta opción no se puede ocultar: sin ella no se podría administrar el menú.");
                return ResponseEntity.ok(response);
            }

            boolean nuevo = nodo.getIdOpcion() == null;
            nodo.setCodigo(codigoNormalizado);
            nodo.setDescripcion(descripcionNormalizada);
            nodo.setTipo(tipo);
            nodo.setIcono(vacioANulo(icono));
            nodo.setColorClase(vacioANulo(colorClase));
            nodo.setBadge(vacioANulo(badge));
            nodo.setUrl(urlN);
            nodo.setRutaBase(rutaBaseN);
            // Un permiso puro nunca se muestra en el menú (no tiene a dónde navegar).
            nodo.setVisible("ITEM".equals(tipo) && urlN == null ? false : visible);
            if (nodo.getEstado() == null) {
                nodo.setEstado(OpcionMenu.ESTADO_ACTIVO);
            }

            OpcionMenu guardado = opcionMenuService.guardarNodo(nodo, idPadre);
            registrarYAvisar(request, nuevo ? ActividadService.ACC_REGISTRO : ActividadService.ACC_MODIFICACION,
                    guardado, (nuevo ? "Nueva opción de menú: " : "Opción de menú modificada: ") + descripcionNormalizada);

            response.put("ok", true);
            response.put("msg", nuevo
                    ? "Opción creada. Para que otros usuarios la vean, asígnela en sus permisos (Usuarios → Permisos)."
                    : "Opción guardada. El menú de los usuarios conectados ya se actualizó.");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("msg", "Error al guardar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/subir/{id}")
    public ResponseEntity<Map<String, Object>> subir(HttpServletRequest request, @PathVariable("id") Long id) {
        return accion(request, id, "Opción movida", () -> opcionMenuService.moverArriba(id), ActividadService.ACC_MOVIMIENTO, "subió");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/bajar/{id}")
    public ResponseEntity<Map<String, Object>> bajar(HttpServletRequest request, @PathVariable("id") Long id) {
        return accion(request, id, "Opción movida", () -> opcionMenuService.moverAbajo(id), ActividadService.ACC_MOVIMIENTO, "bajó");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/toggle/{id}")
    public ResponseEntity<Map<String, Object>> toggle(HttpServletRequest request, @PathVariable("id") Long id) {
        return accion(request, id, "Visibilidad actualizada", () -> opcionMenuService.alternarVisible(id),
                ActividadService.ACC_MODIFICACION, "cambió la visibilidad de");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/bloquear/{id}")
    public ResponseEntity<Map<String, Object>> bloquear(HttpServletRequest request, @PathVariable("id") Long id,
            @RequestParam("bloquear") boolean bloquear) {
        return accion(request, id, bloquear ? "Opción bloqueada: nadie salvo ADMINISTRADOR puede entrar."
                        : "Opción desbloqueada.",
                () -> opcionMenuService.bloquear(id, bloquear),
                bloquear ? ActividadService.ACC_BLOQUEO : ActividadService.ACC_DESBLOQUEO,
                bloquear ? "bloqueó" : "desbloqueó");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(HttpServletRequest request, @PathVariable("id") Long id) {
        return accion(request, id, "Opción eliminada (queda en la papelera, se puede restaurar).",
                () -> opcionMenuService.eliminarNodo(id), ActividadService.ACC_ELIMINACION, "eliminó");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/restaurar/{id}")
    public ResponseEntity<Map<String, Object>> restaurar(HttpServletRequest request, @PathVariable("id") Long id) {
        return accion(request, id, "Opción restaurada. Los permisos que tenía no vuelven: asígnela de nuevo a quien corresponda.",
                () -> opcionMenuService.restaurarNodo(id), ActividadService.ACC_REGISTRO, "restauró");
    }

    /** Cuerpo: {"idPadre": 12 | null, "ids": [5, 9, 7]} — el nuevo orden de esos hijos. */
    @ValidarUsuarioAutenticado
    @PostMapping("/reordenar")
    public ResponseEntity<Map<String, Object>> reordenar(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        ResponseEntity<Map<String, Object>> sinAcceso = exigirAcceso(request);
        if (sinAcceso != null) return sinAcceso;
        try {
            Long idPadre = cuerpo.get("idPadre") instanceof Number n ? n.longValue() : null;
            List<Long> ids = new ArrayList<>();
            if (cuerpo.get("ids") instanceof List<?> lista) {
                for (Object o : lista) {
                    if (o instanceof Number n) ids.add(n.longValue());
                    else if (o != null && !o.toString().isBlank()) ids.add(Long.parseLong(o.toString()));
                }
            }
            opcionMenuService.reordenar(idPadre, ids);
            OpcionMenu padre = idPadre != null ? opcionMenuService.findById(idPadre) : null;
            registrarYAvisar(request, ActividadService.ACC_MOVIMIENTO, padre,
                    "Reordenó el menú" + (padre != null ? " dentro de «" + padre.getDescripcion() + "»" : " (secciones)"));
            return ResponseEntity.ok(Map.of("ok", true, "msg", "Orden guardado"));
        } catch (IllegalStateException e) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "msg", "No se pudo guardar el orden: " + e.getMessage()));
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> accion(HttpServletRequest request, Long id, String msgOk,
            Runnable cambio, String accionActividad, String verbo) {
        ResponseEntity<Map<String, Object>> sinAcceso = exigirAcceso(request);
        if (sinAcceso != null) return sinAcceso;
        Map<String, Object> response = new HashMap<>();
        try {
            OpcionMenu antes = opcionMenuService.findById(id);
            String nombre = antes != null ? antes.getDescripcion() : ("#" + id);
            cambio.run();
            registrarYAvisar(request, accionActividad, opcionMenuService.findById(id),
                    "Se " + verbo + " «" + nombre + "» en el menú");
            response.put("ok", true);
            response.put("msg", msgOk);
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            response.put("ok", false);
            response.put("msg", e.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("msg", "No se pudo completar la acción: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** Monitoreo de actividad + aviso en vivo a todos los menús abiertos. */
    private void registrarYAvisar(HttpServletRequest request, String accion, OpcionMenu nodo, String descripcion) {
        sesionPermisos.menuCambio(descripcion);
        actividadService.registrar(RolesSciaf.usuarioDe(request), ActividadService.MOD_MENU, accion,
                nodo != null ? nodo.getCodigo() : "menú", descripcion, nodo != null ? nodo.getIdOpcion() : null);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map<String, Object>> exigirAcceso(HttpServletRequest request) {
        Usuario u = RolesSciaf.usuarioDe(request);
        if (u == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("ok", false, "msg", "Sesión expirada."));
        }
        if (RolesSciaf.esAdministrador(u)) return null;
        Object ops = request.getSession(false) != null ? request.getSession(false).getAttribute("opciones") : null;
        if (ops instanceof Set<?> s && ((Set<String>) s).contains(CODIGO_GESTION)) return null;
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("ok", false, "msg", "No tiene permiso para modificar el menú."));
    }

    private boolean esProtegida(OpcionMenu nodo) {
        return com.usic.SistemasActivosFijosUAP.model.ServiceImpl.OpcionMenuServiceImpl.CODIGOS_PROTEGIDOS
                .contains(nodo.getCodigo());
    }

    private String vacioANulo(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
