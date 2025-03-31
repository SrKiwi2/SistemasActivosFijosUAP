package com.usic.SistemasActivosFijosUAP.controller.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LoginController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private final IUsuarioService usuarioService;

    @GetMapping(value = "/")
    public String formLogin() {

        return "login/login";
    }

    @PostMapping("/iniciar-sesion")
    public ResponseEntity<String> iniciarSesion(@RequestParam(value = "usuario") String user,
            @RequestParam(value = "contrasena") String contrasena, Model model, HttpServletRequest request,
            RedirectAttributes flash) {

        // los dos parametros de usuario, contraseña vienen del formulario html
        Usuario usuario = usuarioService.UsuarioyContraseña(user, contrasena);

        if (usuario != null) {
            if (usuario.getEstado().equals("INACTIVO")) {
                return ResponseEntity.ok("Este usuario esta en estado inactivo!");
            }
            HttpSession sessionAdministrador = request.getSession(true);
            sessionAdministrador.setAttribute("usuario", usuario);
            sessionAdministrador.setAttribute("persona", usuario.getPersona());
            sessionAdministrador.setAttribute("nombre_rol", usuario.getRol().getNombre());

            flash.addAttribute("success", usuario.getPersona().getNombre());

            return ResponseEntity.ok("Iniciando Session");

        } else {
            // flash.addFlashAttribute("error", "Usuario o contraseña incorrectos.");
            return ResponseEntity.ok("Usuario o contraseña incorrectos!");
        }

    }

    @ValidarUsuarioAutenticado
    @RequestMapping("/cerrar_sesion")
    public String cerrarSesion(HttpServletRequest request, RedirectAttributes flash) {
        Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
        HttpSession sessionAdministrador = request.getSession();
        if (sessionAdministrador != null) {
            sessionAdministrador.invalidate();
            flash.addAttribute("validado", "Se cerro sesion con exito");
            logger.info("Usuario cerro sesión: {}", usuarioLogueado.getPersona().getNombre());
            
        }
        return "redirect:/";
    }
}
