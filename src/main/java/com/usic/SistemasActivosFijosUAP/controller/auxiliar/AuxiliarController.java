package com.usic.SistemasActivosFijosUAP.controller.auxiliar;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/auxiliar")
@RequiredArgsConstructor
public class AuxiliarController {

    private final IAuxiliarService auxiliarService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_auxiliar() {
        return "auxiliar/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_auxiliar(Model model) throws Exception {
        List<Auxiliar> listasAuxiliares = auxiliarService.findAll();
        List<String> encryptedIds = new ArrayList<>();
        for (Auxiliar auxiliares : listasAuxiliares) {
            String id_encryptado = Encriptar.encrypt(Long.toString(auxiliares.getIdAuxiliar()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasAuxiliares", listasAuxiliares);
        model.addAttribute("id_encryptado", encryptedIds);
        return "auxiliar/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario_auxiliar(Model model, Auxiliar auxiliar) {
        return "auxiliar/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_auxiliar}")
    public String formularioEdit_auxiliar(Model model, @PathVariable("id_auxiliar") String idAuxiliar) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idAuxiliar));
        model.addAttribute("auxiliar", auxiliarService.findById(id));
        model.addAttribute("edit", "true");
        return "auxiliar/formulario";
    }

    @PostMapping(value = "/modificar-auxiliar")
    public ResponseEntity<String> modificar_auxiliar(HttpServletRequest request, Auxiliar auxiliar,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        auxiliar.setModificacionIdUsuario(usuario.getIdUsuario());
        auxiliar.setEstado("ACTIVO");
        auxiliarService.save(auxiliar);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_auxiliar}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_auxiliar") String idAuxiliar) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idAuxiliar));
        Auxiliar auxiliar = auxiliarService.findById(id);
        auxiliar.setEstado("ELIMINADO");
        auxiliarService.save(auxiliar);
        return ResponseEntity.ok("Registro Eliminado");
    }
}
