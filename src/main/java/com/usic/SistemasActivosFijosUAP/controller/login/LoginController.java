package com.usic.SistemasActivosFijosUAP.controller.login;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.IService.LogAccesoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionPermisosService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class LoginController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    private final IUsuarioService usuarioService;
    private final IUsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final SesionPermisosService sesionPermisosService;
    private final LogAccesoService logAccesoService;

    @GetMapping(value = "/login")
    public String formLogin() {
        return "login/login";
    }

    @PostMapping("/iniciar-sesion")
    public ResponseEntity<String> iniciarSesion(
        @RequestParam String usuario,
        @RequestParam String contrasena, 
        HttpServletRequest request, 
        RedirectAttributes flash) {

        try {
            Usuario usuario_ = usuarioService.buscarUsuarioPorNombre(usuario);

            if (usuario_ == null) {
                // buscarUsuarioPorNombre solo mira ACTIVOS: un desactivado caía en "contraseña
                // incorrecta". Se le dice que está desactivado, pero SOLO si la contraseña es la
                // suya: si no, cualquiera podría averiguar qué usuarios existen.
                for (Usuario inactivo : usuarioDao.inactivosPorNombre(usuario)) {
                    if (passwordEncoder.matches(contrasena, inactivo.getPassword())) {
                        registrarAcceso(usuario, inactivo, false, "USUARIO_INACTIVO", request);
                        return ResponseEntity.ok("Su usuario está desactivado. Comuníquese con el administrador del sistema");
                    }
                }
            }

            if (usuario_ == null || !passwordEncoder.matches(contrasena, usuario_.getPassword())) {
                registrarAcceso(usuario, usuario_, false, "CREDENCIALES_INVALIDAS", request);
                return ResponseEntity.ok("Usuario o contraseña incorrectos!");
            }

            if ("INACTIVO".equals(usuario_.getEstado()) || "ELIMINADO".equals(usuario_.getEstado())) {
                registrarAcceso(usuario, usuario_, false, "USUARIO_INACTIVO", request);
                return ResponseEntity.ok("Este usuario está en estado inactivo!");
            }

            HttpSession session = request.getSession(true);

            String rol = (usuario_.getRol() != null && usuario_.getRol().getNombre() != null)
                    ? usuario_.getRol().getNombre().toUpperCase()
                    : "";

            // Usuario, persona, rol y permisos de menú. Quedan "versionados": si el
            // administrador los cambia después, la sesión se actualiza sola.
            sesionPermisosService.iniciar(session, usuario_);
            registrarAcceso(usuario, usuario_, true, "LOGIN_OK", request);

            flash.addAttribute("success", usuario_.getPersona().getNombre());

            logger.info("Usuario inició sesión: {} - Rol: {}", 
                usuario_.getPersona().getNombre(), rol);

            String respuesta = determinarRespuestaLogin(rol);
            
            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            logger.error("Error en inicio de sesión", e);
            return ResponseEntity.ok("Error al iniciar sesión: " + e.getMessage());
        }
    }

    /**
     * Deja el intento en log_acceso (la pantalla de usuarios muestra el último ingreso y
     * los intentos fallidos). Nunca puede impedir el inicio de sesión.
     */
    private void registrarAcceso(String nombre, Usuario u, boolean exito, String motivo, HttpServletRequest request) {
        try {
            logAccesoService.registrar(nombre, u != null ? u.getIdUsuario() : null,
                    u != null && u.getRol() != null ? u.getRol().getNombre() : null,
                    exito, motivo, ipDe(request), request.getHeader("User-Agent"),
                    exito && request.getSession(false) != null ? request.getSession(false).getId() : null);
        } catch (Exception e) {
            logger.warn("No se pudo registrar el acceso de {}: {}", nombre, e.getMessage());
        }
    }

    private String ipDe(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String determinarRespuestaLogin(String rol) {
        switch (rol) {
            case "ADMINISTRADOR":
                return "Iniciando Session";

            case "SUPER USUARIO":
                return "Iniciando Session";

            case "APOYO":
                return "Iniciando Session";
            
            case "RECEPCION":
                return "Inicio Recepcion";
            
            case "RESPONSABLE":
                return "Inicio Responsable";
            
            default:
                return "Iniciando Session";
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

    /**
     * Endpoint para verificar sesión activa (usado por AJAX)
     */
    @GetMapping("/verificar-sesion")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verificarSesion(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        HttpSession session = request.getSession(false);
        Usuario usuario = (Usuario) (session != null ? session.getAttribute("usuario") : null);
        
        if (usuario != null) {
            response.put("ok", true);
            response.put("usuario", usuario.getUsuario());
            response.put("rol", usuario.getRol().getNombre());
        } else {
            response.put("ok", false);
            response.put("msg", "Sesión expirada");
        }
        
        return ResponseEntity.ok(response);
    }
}
