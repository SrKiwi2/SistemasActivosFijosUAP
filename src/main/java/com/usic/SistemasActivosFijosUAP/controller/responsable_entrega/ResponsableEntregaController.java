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

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista() {
        return "responsable_entrega/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model,
            @RequestParam(name = "q", required = false) String q) throws Exception {
        List<ResponsableEntrega> lista = responsableEntregaService.buscarPorQ(q);
        List<String> encryptedIds = new ArrayList<>();
        for (ResponsableEntrega r : lista) {
            encryptedIds.add(Encriptar.encrypt(r.getIdResponsableEntrega().toString()));
        }
        model.addAttribute("lista", lista);
        model.addAttribute("id_encryptado", encryptedIds);
        return "responsable_entrega/tabla_registro";
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

        original.setNombre(form.getNombre());
        original.setCargo(form.getCargo());
        original.setGenero(form.getGenero());
        original.setUsuario(usuario != null ? usuario.getUsuario() : "SISTEMA");
        original.setFechaUlt(LocalDate.now());

        responsableEntregaService.save(original);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Modificado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_responsable}")
    public ResponseEntity<String> eliminar(@PathVariable("id_responsable") String idEnc) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idEnc));
        ResponsableEntrega r = responsableEntregaService.findById(id);
        if (r != null) {
            r.setEstado("ELIMINADO");
            responsableEntregaService.save(r);
        }
        return ResponseEntity.ok("Eliminado");
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
    @GetMapping("/api/seleccionado")
    @ResponseBody
    public ResponseEntity<?> apiSeleccionado() {
        ResponsableEntrega r = responsableEntregaService.findSeleccionado();
        if (r == null) return ResponseEntity.ok(null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getIdResponsableEntrega());
        m.put("nombre", r.getNombre());
        m.put("genero", r.getGenero());
        return ResponseEntity.ok(m);
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

    @ValidarUsuarioAutenticado
    @GetMapping("/api/detalle/{idEnc}")
    @ResponseBody
    public ResponseEntity<?> apiDetalle(@PathVariable String idEnc) {
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            ResponsableEntrega r = responsableEntregaService.findById(id);
            if (r == null) return ResponseEntity.notFound().build();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idResponsableEntrega", r.getIdResponsableEntrega());
            m.put("nombre", r.getNombre());
            m.put("cargo", r.getCargo());
            m.put("estado", r.getEstado());
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }
}
