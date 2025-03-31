package com.usic.SistemasActivosFijosUAP.controller.usuario;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IRolService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/usuario")
@RequiredArgsConstructor
public class UsuarioController {
    
    private final IUsuarioService usuarioService;
    private final IPersonaService personaService;
    private final IRolService rolService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "usuario/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {

        List<Usuario> listaUsuarios = usuarioService.listarUsuarios();
        List<String> encryptedIds = new ArrayList<>();
        for (Usuario usuarios : listaUsuarios) {
            String id_encryptado = Encriptar.encrypt(Long.toString(usuarios.getIdUsuario()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listaUsuarios", listaUsuarios);
        model.addAttribute("id_encryptado", encryptedIds);

        return "usuario/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Usuario usuario) {

        model.addAttribute("listaPersonas", personaService.listarPersonas());
        model.addAttribute("listaRoles", rolService.listarRoles());

        return "usuario/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_usuario}")
    public String formularioEdit(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idUsuario));
        model.addAttribute("usuario", usuarioService.findById(id));
        model.addAttribute("listaPersonas", personaService.listarPersonas());
        model.addAttribute("listaRoles", rolService.listarRoles());
        model.addAttribute("edit", "true");

        return "usuario/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-usuario")
    public ResponseEntity<String> registrar(HttpServletRequest request, @Validated Usuario usuario) {

        if (usuarioService.UsuarioyContraseña(usuario.getUsuario(), usuario.getPassword()) == null) {
            Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
            usuario.setRegistroIdUsuario(usuarioLogueado.getIdUsuario());
            usuario.setEstado("ACTIVO");
            usuarioService.save(usuario);

            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe el usuario y contraseña");
        }
    }

    @PostMapping(value = "/modificar-usuario")
    public ResponseEntity<String> modificar(HttpServletRequest request, Usuario usuario,
            RedirectAttributes redirectAttrs) {

        Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
        usuario.setModificacionIdUsuario(usuarioLogueado.getIdUsuario());
        usuario.setEstado("ACTIVO");
        usuarioService.save(usuario);

        return ResponseEntity.ok("Se realizó la modificación correctamente");

    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_usuario}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idUsuario));
        Usuario usuario = usuarioService.findById(id);
        usuario.setEstado("ELIMINADO");
        usuarioService.save(usuario);

        return ResponseEntity.ok("Registro Eliminado");
    }
}
