package com.usic.SistemasActivosFijosUAP.controller.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
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
    private final PasswordEncoder passwordEncoder;

    @GetMapping(value = "/login")
    public String formLogin() {

        return "login/login";
    }

    @PostMapping("/iniciar-sesion")
    public ResponseEntity<String> iniciarSesion(
        @RequestParam String usuario,
        @RequestParam String contrasena, 
        HttpServletRequest request, RedirectAttributes flash) {

        // los dos parametros de usuario, contraseña vienen del formulario html
        Usuario usuario_ = usuarioService.buscarUsuarioPorNombre(usuario);
        if (usuario_ == null || !passwordEncoder.matches(contrasena, usuario_.getPassword())) {
            return ResponseEntity.ok("Usuario o contraseña incorrectos!");
        }

        if ("INACTIVO".equals(usuario_.getEstado())) {
            return ResponseEntity.ok("Este usuario esta en estado inactivo!");
        }

            HttpSession session = request.getSession(true);
            session.setAttribute("usuario", usuario_);
            session.setAttribute("persona", usuario_.getPersona());

            String rol = (usuario_.getRol() != null && usuario_.getRol().getNombre() != null)
                    ? usuario_.getRol().getNombre().toUpperCase()
                    : "";

            session.setAttribute("nombre_rol", usuario_.getRol().getNombre());
            flash.addAttribute("success", usuario_.getPersona().getNombre());

            // 4) Respuesta según rol
            String respuesta = "RESPONSABLE".equals(rol) ? "Inicio Responsable" : "Iniciando Session";
            return ResponseEntity.ok(respuesta);
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
