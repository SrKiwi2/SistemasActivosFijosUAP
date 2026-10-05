package com.usic.SistemasActivosFijosUAP.controller.responsable_entrega;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableEntregaService;
import com.usic.SistemasActivosFijosUAP.model.entity.ResponsableEntrega;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/responsable-entrega")
@RequiredArgsConstructor
public class ResponsableEntregaController {

    private final IResponsableEntregaService responsableEntregaService;

    /** La pantalla llega con la tabla ya armada: un solo pedido al abrir. */
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista(Model model) throws Exception {
        cargarTabla(model, null);
        return "responsable_entrega/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model,
            @RequestParam(name = "q", required = false) String q) throws Exception {
        cargarTabla(model, q);
        return "responsable_entrega/tabla_registro";
    }

    private void cargarTabla(Model model, String q) throws Exception {
        List<ResponsableEntrega> lista = responsableEntregaService.buscarPorQ(q);
        List<String> encryptedIds = new ArrayList<>();
        for (ResponsableEntrega r : lista) {
            encryptedIds.add(Encriptar.encrypt(r.getIdResponsableEntrega().toString()));
        }
        model.addAttribute("lista", lista);
        model.addAttribute("id_encryptado", encryptedIds);
    }

    /** En MAYÚSCULAS (figura así en el acta) y sin espacios sobrantes; vacío pasa a null. */
    private static String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, ResponsableEntrega responsableEntrega) {
        return "responsable_entrega/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_responsable}")
    public String formularioEdit(Model model, @PathVariable("id_responsable") String idEnc) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idEnc));
        model.addAttribute("responsableEntrega", responsableEntregaService.findById(id));
        model.addAttribute("edit", "true");
        return "responsable_entrega/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(
            HttpServletRequest request,
            @Validated @ModelAttribute ResponsableEntrega responsableEntrega,
            BindingResult br) {
        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                    .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                    .toList()
            ));
        }

        responsableEntrega.setIdResponsableEntrega(null);   // un alta nunca pisa otro registro
        responsableEntrega.setNombre(limpiar(responsableEntrega.getNombre()));
        responsableEntrega.setCargo(limpiar(responsableEntrega.getCargo()));
        if (responsableEntrega.getNombre() == null) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "Ingrese el nombre."));
        }
        if (!responsableEntregaService.isNombreUnique(responsableEntrega.getNombre(), null)) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "Ya existe una persona de entrega con ese nombre."));
        }
        responsableEntrega.setSeleccionado(false);   // se elige con el botón "Seleccionar"

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        responsableEntrega.setUsuario(usuario != null ? usuario.getUsuario() : "SISTEMA");
        responsableEntrega.setFechaUlt(LocalDate.now());
        responsableEntrega.setEstado("ACTIVO");
        if (usuario != null) {
            responsableEntrega.setRegistroIdUsuario(usuario.getIdUsuario());
        }

        responsableEntregaService.save(responsableEntrega);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Registrado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/modificar")
    public ResponseEntity<?> modificar(
            HttpServletRequest request,
            @Validated @ModelAttribute ResponsableEntrega form,
            BindingResult br) {
        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                    .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                    .toList()
            ));
        }

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        ResponsableEntrega original = responsableEntregaService.findById(form.getIdResponsableEntrega());
        if (original == null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No encontrado"));
        }

        String nombre = limpiar(form.getNombre());
        if (nombre == null) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "Ingrese el nombre."));
        }
        if (!responsableEntregaService.isNombreUnique(nombre, original.getIdResponsableEntrega())) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "Ya existe otra persona de entrega con ese nombre."));
        }
        original.setNombre(nombre);
        original.setCargo(limpiar(form.getCargo()));
        if (usuario != null) original.setModificacionIdUsuario(usuario.getIdUsuario());
        original.setGenero(form.getGenero());
        original.setUsuario(usuario != null ? usuario.getUsuario() : "SISTEMA");
        original.setFechaUlt(LocalDate.now());

        responsableEntregaService.save(original);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Modificado correctamente"));
    }

    /**
     * JSON { ok, msg } como el resto. Si era la persona seleccionada para el acta, deja de
     * estarlo y se avisa: el acta de asignación queda sin responsable hasta elegir otra.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_responsable}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> eliminar(HttpServletRequest request,
            @PathVariable("id_responsable") String idEnc) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idEnc));
        ResponsableEntrega r = responsableEntregaService.findById(id);
        if (r == null) return ResponseEntity.ok(Map.of("ok", false, "msg", "No encontrado."));
        boolean eraSeleccionado = Boolean.TRUE.equals(r.getSeleccionado());
        r.setEstado("ELIMINADO");
        r.setSeleccionado(false);
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) r.setModificacionIdUsuario(usuario.getIdUsuario());
        responsableEntregaService.save(r);
        return ResponseEntity.ok(Map.of("ok", true, "msg", eraSeleccionado
                ? "Eliminado. Era quien figuraba en el acta: seleccione a otra persona."
                : "Eliminado"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/seleccionar/{id}")
    public ResponseEntity<?> seleccionar(@PathVariable("id") String idEnc) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idEnc));
        ResponsableEntrega r = responsableEntregaService.findById(id);
        if (r == null) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No encontrado"));
        r.setSeleccionado(true);
        responsableEntregaService.save(r);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Seleccionado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/listar")
    @ResponseBody
    public List<Map<String, Object>> apiListar() {
        return responsableEntregaService.listarActivos().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getIdResponsableEntrega());
            m.put("nombre", r.getNombre());
            m.put("cargo", r.getCargo() != null ? r.getCargo() : "");
            m.put("genero", r.getGenero() != null ? r.getGenero() : "");
            m.put("seleccionado", r.getSeleccionado() != null && r.getSeleccionado());
            return m;
        }).toList();
    }

}
