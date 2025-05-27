package com.usic.SistemasActivosFijosUAP.controller.entidad;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/entidad")
@RequiredArgsConstructor
public class EntidadController {
    private final IEntidadService entidadService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioEntidad() {
        return "entidad/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistrosEntidad(Model model) throws Exception {
        List<Entidad> listasEntidades = entidadService.listarEntidad();
        List<String> encryptedIds = new ArrayList<>();
        for (Entidad entidades : listasEntidades) {
            String id_encryptado = Encriptar.encrypt(Long.toString(entidades.getIdEntidad()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasEntidades", listasEntidades);
        model.addAttribute("id_encryptado", encryptedIds);
        return "entidad/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioEntidad(Model model, Entidad entidad) {
        return "entidad/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_entidad}")
    public String formularioEditEntidad(Model model, @PathVariable("id_entidad") String idEntidad) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idEntidad));
        model.addAttribute("entidad", entidadService.findById(id));
        model.addAttribute("edit", "true");
        return "entidad/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-entidad")
    public ResponseEntity<String> RegistrarEntidad(HttpServletRequest request, @Validated Entidad entidad) {
        if (entidadService.buscarPorNombre(entidad.getNombre()) == null) {
            entidad.setEstado("ACTIVO");
            entidadService.save(entidad);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    @PostMapping(value = "/modificar-entidad")
    public ResponseEntity<String> modificarEntidad(HttpServletRequest request, Entidad entidad,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        entidad.setModificacionIdUsuario(usuario.getIdUsuario());
        entidad.setEstado("ACTIVO");
        entidadService.save(entidad);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_entidad}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_entidad") String idEntidad) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idEntidad));
        Entidad entidad = entidadService.findById(id);
        entidad.setEstado("ELIMINADO");
        entidadService.save(entidad);
        return ResponseEntity.ok("Registro Eliminado");
    }
}
