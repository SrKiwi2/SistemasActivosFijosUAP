package com.usic.SistemasActivosFijosUAP.controller.rol;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IRolService;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/rol")
@RequiredArgsConstructor
public class RolController {
    
    private final IRolService rolService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "rol/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {
        List<Rol> listaRoles = rolService.listarRoles();
        List<String> encryptedIds = new ArrayList<>();
        for (Rol roles : listaRoles) {
            String id_encryptado = Encriptar.encrypt(Long.toString(roles.getIdRol()));
            encryptedIds.add(id_encryptado);
        }

        model.addAttribute("listaRoles", listaRoles);
        model.addAttribute("id_encryptado", encryptedIds);

        return "rol/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Rol rol) {
        
        return "rol/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_rol}")
    public String formularioEdit(Model model, @PathVariable("id_rol") String idRol) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idRol));
        model.addAttribute("rol", rolService.findById(id));
        model.addAttribute("edit", "true");
        return "rol/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-rol")
    public ResponseEntity<String> RegistrarPersona(HttpServletRequest request, @Validated Rol rol) {
        if (rolService.buscarRolPorNombre(rol.getNombre()) == null) {
            rol.setEstado("ACTIVO");
            rolService.save(rol);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    @PostMapping(value = "/modificar-rol")
    public ResponseEntity<String> modificar(HttpServletRequest request, Rol rol,
            RedirectAttributes redirectAttrs) {
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        rol.setModificacionIdUsuario(usuario.getIdUsuario());
        rol.setEstado("ACTIVO");
        rolService.save(rol);

        return ResponseEntity.ok("Se realizó el registro correctamente");

    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_rol}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_rol") String idRol) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idRol));
        Rol rol = rolService.findById(id);
        rol.setEstado("ELIMINADO");
        rolService.save(rol);
        return ResponseEntity.ok("Registro Eliminado");
    }
}
