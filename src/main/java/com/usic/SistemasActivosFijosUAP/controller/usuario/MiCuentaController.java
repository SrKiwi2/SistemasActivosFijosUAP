package com.usic.SistemasActivosFijosUAP.controller.usuario;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.GestionUsuariosService;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Lo que cada usuario hace sobre su propia cuenta (menú del avatar → "Cambiar mi
 * contraseña"). Vive fuera de /administracion/usuario a propósito: no hace falta tener el
 * permiso de gestión de usuarios para cambiar la contraseña propia.
 */
@RestController
@RequestMapping("/adm/mi-cuenta")
public class MiCuentaController {

    private final GestionUsuariosService gestion;

    public MiCuentaController(GestionUsuariosService gestion) {
        this.gestion = gestion;
    }

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
