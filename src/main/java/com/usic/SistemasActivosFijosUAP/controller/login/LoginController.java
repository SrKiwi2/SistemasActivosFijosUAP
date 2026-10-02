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

import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.IService.LogAccesoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.RecordarmeService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionInactividadService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionPermisosService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final SesionInactividadService sesionInactividadService;
    private final RecordarmeService recordarme;

    @GetMapping(value = "/login")
    public String formLogin() {
        return "login/login";
    }

    @PostMapping("/iniciar-sesion")
    public ResponseEntity<String> iniciarSesion(
        @RequestParam String usuario,
        @RequestParam String contrasena,
        @RequestParam(name = "recordar", defaultValue = "false") boolean recordar,
        HttpServletRequest request,
        HttpServletResponse response,
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

            // Sesión NUEVA en cada ingreso: si quedaba una (de otro usuario, o una vencida),
            // se descarta. Además cambia la cookie, y así el navegador no puede mostrar con
            // "Atrás" pantallas guardadas de la sesión anterior (fijación de sesión, bfcache).
            HttpSession anterior = request.getSession(false);
            // La sesión anterior de este navegador (y su "recordarme") queda cerrada.
            recordarme.cerrar(request, response, anterior, "REEMPLAZADA");
            if (anterior != null) {
                try {
                    anterior.invalidate();
                } catch (IllegalStateException yaInvalida) {
                    // nada que hacer
                }
            }
            HttpSession session = request.getSession(true);

            String rol = (usuario_.getRol() != null && usuario_.getRol().getNombre() != null)
                    ? usuario_.getRol().getNombre().toUpperCase()
                    : "";

            // Usuario, persona, rol y permisos de menú. Quedan "versionados": si el
            // administrador los cambia después, la sesión se actualiza sola.
            sesionPermisosService.iniciar(session, usuario_);
            sesionInactividadService.iniciar(session);
            // Sesión de este equipo en "Mis sesiones abiertas" (+ cookie si marcó "mantener").
            // Si falla, el ingreso sigue (sin "recordarme"): nunca se deja a nadie afuera por esto.
            try {
                recordarme.abrir(request, response, session, usuario_, recordar);
            } catch (Exception e) {
                logger.error("No se pudo registrar la sesión de {} en sesion_usuario", usuario, e);
            }
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

    /**
     * Sin @ValidarUsuarioAutenticado a propósito: cerrar sesión siempre debe funcionar,
     * también si la sesión ya venció (antes eso rebotaba por /form-login, que no existe).
     */
    @RequestMapping("/cerrar_sesion")
    public String cerrarSesion(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        // Cierra la sesión de este equipo (también la recordada) y borra su cookie. Si la base
        // falla, igual se sale: la sesión HTTP se invalida abajo pase lo que pase.
        try {
            recordarme.cerrar(request, response, session, "LOGOUT");
        } catch (Exception e) {
            logger.error("No se pudo cerrar en sesion_usuario la sesión que sale", e);
            recordarme.olvidar(request, response);
        }
        if (session != null) {
            try {
                Usuario usuarioLogueado = (Usuario) session.getAttribute("usuario");
                session.invalidate();
                if (usuarioLogueado != null && usuarioLogueado.getPersona() != null) {
                    logger.info("Usuario cerró sesión: {}", usuarioLogueado.getPersona().getNombre());
                }
            } catch (IllegalStateException yaInvalida) {
                // ya estaba cerrada
            }
        }
        // Se borra la cookie: con la cookie cambiada el navegador descarta las pantallas
        // que guarda para "Atrás" (bfcache) y no muestra el sistema como si siguiera adentro.
        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return "redirect:/?sesion=cerrada";
    }

    /**
     * Estado de la sesión para el aviso de inactividad (sciaf-inactividad.js). El navegador
     * informa hace cuántos segundos no se toca el teclado ni el mouse en esa pestaña: eso es
     * lo único que cuenta como actividad (además de abrir pantallas). Esta consulta en sí
     * NO renueva la sesión.
     */
    @PostMapping("/api/sesion/estado")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> estadoSesion(HttpServletRequest request,
            @RequestParam(name = "inactivoSeg", defaultValue = "0") long inactivoSeg) {
        HttpSession session = sesionConUsuario(request);
        if (session == null) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body(Map.of("activa", false));
        }
        long seg = Math.max(0, inactivoSeg);
        sesionInactividadService.marcar(session, System.currentTimeMillis() - seg * 1000);
        return ResponseEntity.ok(sesionInactividadService.estado(session));
    }

    /** "Seguir conectado" en el aviso de inactividad. */
    @PostMapping("/api/sesion/renovar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> renovarSesion(HttpServletRequest request) {
        HttpSession session = sesionConUsuario(request);
        if (session == null) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED).body(Map.of("activa", false));
        }
        sesionInactividadService.marcar(session, System.currentTimeMillis());
        return ResponseEntity.ok(sesionInactividadService.estado(session));
    }

    private HttpSession sesionConUsuario(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        try {
            return (session != null && session.getAttribute("usuario") != null) ? session : null;
        } catch (IllegalStateException yaInvalida) {
            return null;
        }
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
