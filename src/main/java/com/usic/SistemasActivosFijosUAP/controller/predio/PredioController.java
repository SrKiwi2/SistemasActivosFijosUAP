package com.usic.SistemasActivosFijosUAP.controller.predio;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/predio")
@RequiredArgsConstructor
public class PredioController {
    private final IPredioServicio predioServicio;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_predio() {
        return "predio/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_municipio(Model model) throws Exception {
        List<Predio> listasPredios = predioServicio.findAll();
        List<String> encryptedIds = new ArrayList<>();
        for (Predio predios : listasPredios) {
            String id_encryptado = Encriptar.encrypt(Long.toString(predios.getIdPredio()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasPredios", listasPredios);
        model.addAttribute("id_encryptado", encryptedIds);
        return "predio/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario_predio(Model model, Predio predio) {
        return "predio/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_predio}")
    public String formularioEdit_predio(Model model, @PathVariable("id_predio") String idPredio) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idPredio));
        model.addAttribute("predio", predioServicio.findById(id));
        model.addAttribute("edit", "true");
        return "predio/formulario";
    }

    // @ValidarUsuarioAutenticado
    // @PostMapping("/registrar-predio")
    // public ResponseEntity<String> Registrar_predio(HttpServletRequest request, @Validated Predio predio) {
    //     if (predioServicio.buscarPorNombre(predio.getNombre()) == null) {
    //         predio.setEstado("ACTIVO");
    //         predioServicio.save(predio);
    //         return ResponseEntity.ok("Se realizó el registro correctamente");
    //     } else {
    //         return ResponseEntity.ok("Ya existe un rol con este nombre");
    //     }
    // }

    @PostMapping(value = "/modificar-predio")
    public ResponseEntity<String> modificar_predio(HttpServletRequest request, Predio predio,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        predio.setModificacionIdUsuario(usuario.getIdUsuario());
        predio.setEstado("ACTIVO");
        predioServicio.save(predio);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_predio}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_predio") String idPredio) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idPredio));
        Predio predio = predioServicio.findById(id);
        predio.setEstado("ELIMINADO");
        predioServicio.save(predio);
        return ResponseEntity.ok("Registro Eliminado");
    }
}
