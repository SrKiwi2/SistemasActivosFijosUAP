package com.usic.SistemasActivosFijosUAP.controller.grupo_contable;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/grupoc")
@RequiredArgsConstructor
public class GrupoContableController {

    private final IGrupoContableService grupoContableService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioGrupoContable() {
        return "grupoContable/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {
        List<GrupoContable> listasGrupoContable = grupoContableService.listarGruposContables();
        List<String> encryptedIds = new ArrayList<>();
        for (GrupoContable grupoContables : listasGrupoContable) {
            String id_encryptado = Encriptar.encrypt(Long.toString(grupoContables.getIdGrupoContable()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasGrupoContable", listasGrupoContable);
        model.addAttribute("id_encryptado", encryptedIds);
        return "grupoContable/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioGrupoContable(Model model, GrupoContable grupoContable) {
        return "grupoContable/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_grupo_contable}")
    public String formularioEditGrupoContable(Model model, @PathVariable("id_grupo_contable") String idGrupoContable) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idGrupoContable));
        model.addAttribute("grupoContable", grupoContableService.findById(id));
        model.addAttribute("edit", "true");
        return "grupoContable/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-grupoc")
    public ResponseEntity<String> registrarGrupoContable(HttpServletRequest request, @Validated GrupoContable grupoContable) {
        if (grupoContableService.buscarPorNombre(grupoContable.getNombre()) == null) {
            grupoContable.setEstado("ACTIVO");
            grupoContableService.save(grupoContable);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    @PostMapping(value = "/modificar-grupoc")
    public ResponseEntity<String> modificarGrupoContable(HttpServletRequest request, GrupoContable grupoContable,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        grupoContable.setModificacionIdUsuario(usuario.getIdUsuario());
        grupoContable.setEstado("ACTIVO");
        grupoContableService.save(grupoContable);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_grupo_contable}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_grupo_contable") String idGrupoContable) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idGrupoContable));
        GrupoContable grupoContable = grupoContableService.findById(id);
        grupoContable.setEstado("ELIMINADO");
        grupoContableService.save(grupoContable);
        return ResponseEntity.ok("Registro Eliminado");
    }
}