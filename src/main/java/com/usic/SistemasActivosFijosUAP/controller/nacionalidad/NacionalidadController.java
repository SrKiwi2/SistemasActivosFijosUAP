package com.usic.SistemasActivosFijosUAP.controller.nacionalidad;

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
import com.usic.SistemasActivosFijosUAP.model.IService.INacionalidadService;
import com.usic.SistemasActivosFijosUAP.model.entity.Nacionalidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/nacionalidad")
@RequiredArgsConstructor
public class NacionalidadController {
    
    private final INacionalidadService nacionalidadService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "nacionalidad/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {
        List<Nacionalidad> listaNacionalidades = nacionalidadService.listarNacionalidad();
        List<String> encryptedIds = new ArrayList<>();
        for (Nacionalidad nacionalidades : listaNacionalidades) {
            String id_encryptado = Encriptar.encrypt(Long.toString(nacionalidades.getIdNacionalidad()));
            encryptedIds.add(id_encryptado);
        }

        model.addAttribute("listaNacionalidades", listaNacionalidades);
        model.addAttribute("id_encryptado", encryptedIds);

        return "nacionalidad/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Nacionalidad nacionalidad) {
        
        return "nacionalidad/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_nacionalidad}")
    public String formularioEdit(Model model, @PathVariable("id_nacionalidad") String idNacionalidad) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idNacionalidad));
        model.addAttribute("nacionalidad", nacionalidadService.findById(id));
        model.addAttribute("edit", "true");
        return "nacionalidad/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-nacionalidad")
    public ResponseEntity<String> RegistrarNacionalidad(HttpServletRequest request, @Validated Nacionalidad nacionalidad) {
        if (nacionalidadService.buscarNacionalidadPorNombre(nacionalidad.getNombre()) == null) {
            nacionalidad.setEstado("ACTIVO");
            nacionalidadService.save(nacionalidad);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    @PostMapping(value = "/modificar-nacionalidad")
    public ResponseEntity<String> modificar(HttpServletRequest request, Nacionalidad nacionalidad,
            RedirectAttributes redirectAttrs) {
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        nacionalidad.setModificacionIdUsuario(usuario.getIdUsuario());
        nacionalidad.setEstado("ACTIVO");
        nacionalidadService.save(nacionalidad);

        return ResponseEntity.ok("Se realizó el registro correctamente");

    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_nacionalidad}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_nacionalidad") String idNacionalidad) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idNacionalidad));
        Nacionalidad nacionalidad = nacionalidadService.findById(id);
        nacionalidad.setEstado("ELIMINADO");
        nacionalidadService.save(nacionalidad);
        return ResponseEntity.ok("Registro Eliminado");
    }
}
