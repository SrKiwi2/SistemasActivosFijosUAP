package com.usic.SistemasActivosFijosUAP.controller.persona;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/persona")
@RequiredArgsConstructor
public class PersonaController {
   
    private final IPersonaService personaService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "persona/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {

        List<Persona> listaPersonas = personaService.listarPersonas();
        List<String> encryptedIds = new ArrayList<>();
        for (Persona personas : listaPersonas) {
            String id_encryptado = Encriptar.encrypt(Long.toString(personas.getIdPersona()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listaPersonas", listaPersonas);
        model.addAttribute("id_encryptado", encryptedIds);

        return "persona/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Persona persona) {
        return "persona/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_persona}")
    public String formularioEdit(Model model, @PathVariable("id_persona") String idPersona) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idPersona));
        model.addAttribute("persona", personaService.findById(id));
        model.addAttribute("edit", "true");

        return "persona/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-persona")
    public ResponseEntity<String> registrar(HttpServletRequest request, @Validated Persona persona) {

        if (personaService.buscarPersonaPorCI(persona.getCi()) == null) {
            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            persona.setRegistroIdUsuario(usuario.getIdUsuario());
            persona.setEstado("ACTIVO");
            personaService.save(persona);

            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe una persona con este C.I.");
        }
    }

    @PostMapping(value = "/modificar-persona")
    public ResponseEntity<String> modificar(HttpServletRequest request, Persona persona,
            RedirectAttributes redirectAttrs) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        persona.setModificacionIdUsuario(usuario.getIdUsuario());
        persona.setEstado("ACTIVO");
        personaService.save(persona);

        return ResponseEntity.ok("Se realizó la modificación correctamente");

    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_persona}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_persona") String idPersona) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idPersona));
        Persona persona = personaService.findById(id);
        persona.setEstado("ELIMINADO");
        personaService.save(persona);

        return ResponseEntity.ok("Registro Eliminado");
    }
}
