package com.usic.SistemasActivosFijosUAP.controller.genero;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/genero")
@RequiredArgsConstructor
public class GeneroController {
 
    private final IGeneroService generoService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "genero/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {
        List<Genero> listaGeneros = generoService.listarGeneros();
        List<String> encryptedIds = new ArrayList<>();
        for (Genero generos : listaGeneros) {
            String id_encryptado = Encriptar.encrypt(Long.toString(generos.getIdGenero()));
            encryptedIds.add(id_encryptado);
        }

        model.addAttribute("listaGeneros", listaGeneros);
        model.addAttribute("id_encryptado", encryptedIds);

        return "genero/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Genero genero) {
        
        return "genero/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_genero}")
    public String formularioEdit(Model model, @PathVariable("id_genero") String idGenero) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idGenero));
        model.addAttribute("genero", generoService.findById(id));
        model.addAttribute("edit", "true");
        return "genero/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-genero")
    public ResponseEntity<String> RegistrarSexo(HttpServletRequest request, @Validated Genero genero) {
        if (generoService.buscarGeneroPorNombre(genero.getNombre()) == null) {
            genero.setEstado("ACTIVO");
            generoService.save(genero);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un genero con este nombre");
        }
    }

    @PostMapping(value = "/modificar-genero")
    public ResponseEntity<String> modificar(HttpServletRequest request, Genero genero,
            RedirectAttributes redirectAttrs) {
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        genero.setModificacionIdUsuario(usuario.getIdUsuario());
        genero.setEstado("ACTIVO");
        generoService.save(genero);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_genero}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_genero") String idGenero) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idGenero));
        Genero genero = generoService.findById(id);
        genero.setEstado("ELIMINADO");
        generoService.save(genero);
        return ResponseEntity.ok("Registro Eliminado");
    }
}