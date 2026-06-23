package com.usic.SistemasActivosFijosUAP.controller.menu;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;

import lombok.RequiredArgsConstructor;

/**
 * CRUD de gestión del menú (catálogo opcion_menu): crear, editar, reordenar,
 * mostrar/ocultar y eliminar secciones, grupos e ítems del sidebar.
 *
 * Solo accesible para quien tenga el ítem "Gestión de Menú" (rutaBase
 * /administracion/menu), gobernado por el PermisoOpcionInterceptor.
 */
@Controller
@RequestMapping("/administracion/menu")
@RequiredArgsConstructor
public class OpcionMenuController {

    private final IOpcionMenuService opcionMenuService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista() {
        return "menu/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla")
    public String tabla(Model model) {
        model.addAttribute("arbol", opcionMenuService.obtenerArbolAdmin());
        return "menu/tabla";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model) {
        model.addAttribute("nodo", new OpcionMenu());
        model.addAttribute("edit", false);
        model.addAttribute("idPadreActual", null);
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

        try {
            String codigoNormalizado = (codigo != null) ? codigo.trim() : "";
            if (codigoNormalizado.isEmpty()) {
                response.put("ok", false);
                response.put("msg", "El código es obligatorio.");
                return ResponseEntity.badRequest().body(response);
            }

            // Validar unicidad del código.
            OpcionMenu porCodigo = opcionMenuService.listarTodas().stream()
                    .filter(o -> codigoNormalizado.equals(o.getCodigo()))
                    .findFirst().orElse(null);
            if (porCodigo != null && (idOpcion == null || !porCodigo.getIdOpcion().equals(idOpcion))) {
                response.put("ok", false);
                response.put("msg", "Ya existe una opción con el código '" + codigoNormalizado + "'.");
                return ResponseEntity.ok(response);
            }

            // ITEM requiere url y rutaBase para navegar y aplicar el bloqueo.
            if ("ITEM".equals(tipo)) {
                if (url == null || url.isBlank() || rutaBase == null || rutaBase.isBlank()) {
                    response.put("ok", false);
                    response.put("msg", "Un ítem requiere URL y rutaBase.");
                    return ResponseEntity.ok(response);
                }
                if (idPadre == null) {
                    response.put("ok", false);
                    response.put("msg", "Un ítem debe pertenecer a un grupo.");
                    return ResponseEntity.ok(response);
                }
            }
            if ("GRUPO".equals(tipo) && idPadre == null) {
                response.put("ok", false);
                response.put("msg", "Un grupo debe pertenecer a una sección.");
                return ResponseEntity.ok(response);
            }

            OpcionMenu nodo = (idOpcion != null) ? opcionMenuService.findById(idOpcion) : new OpcionMenu();
            if (nodo == null) {
                response.put("ok", false);
                response.put("msg", "La opción a modificar no existe.");
                return ResponseEntity.badRequest().body(response);
            }

            nodo.setCodigo(codigoNormalizado);
            nodo.setDescripcion(descripcion);
            nodo.setTipo(tipo);
            nodo.setIcono(icono);
            nodo.setColorClase(colorClase);
            nodo.setBadge(badge);
            nodo.setUrl(url);
            nodo.setRutaBase(rutaBase);
            nodo.setVisible(visible);
            if (nodo.getEstado() == null) {
                nodo.setEstado("ACTIVO");
            }

            opcionMenuService.guardarNodo(nodo, idPadre);

            response.put("ok", true);
            response.put("msg", "Opción guardada correctamente");
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
    public ResponseEntity<Map<String, Object>> subir(@PathVariable("id") Long id) {
        opcionMenuService.moverArriba(id);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Movido"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/bajar/{id}")
    public ResponseEntity<Map<String, Object>> bajar(@PathVariable("id") Long id) {
        opcionMenuService.moverAbajo(id);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Movido"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/toggle/{id}")
    public ResponseEntity<Map<String, Object>> toggle(@PathVariable("id") Long id) {
        opcionMenuService.alternarVisible(id);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Visibilidad actualizada"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id}")
    public ResponseEntity<Map<String, Object>> eliminar(@PathVariable("id") Long id) {
        Map<String, Object> response = new HashMap<>();
        try {
            opcionMenuService.eliminarNodo(id);
            response.put("ok", true);
            response.put("msg", "Opción eliminada");
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            response.put("ok", false);
            response.put("msg", e.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("msg", "Error al eliminar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
