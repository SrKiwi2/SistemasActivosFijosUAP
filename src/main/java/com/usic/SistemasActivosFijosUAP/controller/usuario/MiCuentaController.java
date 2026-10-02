package com.usic.SistemasActivosFijosUAP.controller.usuario;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.GestionUsuariosService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionControlService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionPermisosService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Lo que cada usuario hace sobre su propia cuenta (menú del avatar → "Cambiar mi
 * contraseña", "Mis sesiones abiertas"). Vive fuera de /administracion/usuario a propósito: no hace falta tener el
 * permiso de gestión de usuarios para cambiar la contraseña propia.
 */
@RestController
@RequestMapping("/adm/mi-cuenta")
public class MiCuentaController {

    private final GestionUsuariosService gestion;
    private final SesionControlService sesionControl;
    private final SesionPermisosService sesionPermisos;

    public MiCuentaController(GestionUsuariosService gestion, SesionControlService sesionControl,
            SesionPermisosService sesionPermisos) {
        this.gestion = gestion;
        this.sesionControl = sesionControl;
        this.sesionPermisos = sesionPermisos;
    }

    // ── Mis sesiones abiertas ──────────────────────────────────────────────

    @GetMapping("/sesiones")
    public ResponseEntity<Map<String, Object>> misSesiones(HttpServletRequest request) {
        Usuario actor = RolesSciaf.usuarioDe(request);
        if (actor == null) return sinSesion();
        return ResponseEntity.ok(Map.of("ok", true,
                "sesiones", sesionControl.abiertas(actor.getIdUsuario(), idSesionActual(request))));
    }

    @PostMapping("/sesiones/{id}/cerrar")
    public ResponseEntity<Map<String, Object>> cerrarUna(HttpServletRequest request, @PathVariable("id") Long id) {
        Usuario actor = RolesSciaf.usuarioDe(request);
        if (actor == null) return sinSesion();
        try {
            sesionControl.cerrarPropia(actor.getIdUsuario(), id, idSesionActual(request));
            return ResponseEntity.ok(Map.of("ok", true, "msg", "Sesión cerrada en ese equipo"));
        } catch (ReglaNegocioException e) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    @PostMapping("/sesiones/cerrar-otras")
    public ResponseEntity<Map<String, Object>> cerrarOtras(HttpServletRequest request) {
        Usuario actor = RolesSciaf.usuarioDe(request);
        if (actor == null) return sinSesion();
        // Por forzarCierre y no solo en la base: así también se cortan las sesiones que no
        // quedaron registradas (anteriores a este control).
        int n = sesionPermisos.forzarCierre(actor.getIdUsuario(), "Cerró esta sesión desde otro equipo.",
                request.getSession(false), "CERRADA_POR_USUARIO", actor.getIdUsuario());
        return ResponseEntity.ok(Map.of("ok", true, "msg", n == 0 ? "No había otras sesiones abiertas"
                : (n == 1 ? "Se cerró 1 sesión en otro equipo" : "Se cerraron " + n + " sesiones en otros equipos")));
    }

    private Long idSesionActual(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null && s.getAttribute(SesionControlService.ATTR_ID) instanceof Long id ? id : null;
    }

    private ResponseEntity<Map<String, Object>> sinSesion() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("ok", false, "msg", "Su sesión expiró. Vuelva a ingresar."));
    }

    // ── Contraseña ─────────────────────────────────────────────────────────

    @PostMapping("/contrasena")
    public ResponseEntity<Map<String, Object>> cambiarContrasena(HttpServletRequest request,
            @RequestParam("actual") String actual,
            @RequestParam("nueva") String nueva,
            @RequestParam("confirmacion") String confirmacion,
            @RequestParam(value = "cerrarOtras", defaultValue = "false") boolean cerrarOtras) {
        Usuario actor = RolesSciaf.usuarioDe(request);
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Su sesión expiró. Vuelva a ingresar."));
        }
        try {
            gestion.cambiarMiContrasena(actor, request.getSession(false), actual, nueva, confirmacion, cerrarOtras);
            return ResponseEntity.ok(Map.of("ok", true, "msg", "Contraseña actualizada"
                    + (cerrarOtras ? ". Se cerraron sus otras sesiones abiertas." : ".")));
        } catch (ReglaNegocioException e) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", e.getMessage()));
        }
    }
}
