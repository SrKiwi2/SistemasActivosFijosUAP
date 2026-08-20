package com.usic.SistemasActivosFijosUAP.config.movil;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.usic.SistemasActivosFijosUAP.model.service.movil.AuthMovilService.AuthMovilException;

import lombok.extern.slf4j.Slf4j;

/**
 * Errores de los controladores móviles, con un sobre JSON uniforme:
 * <pre>{ "ok": false, "codigo": "...", "mensaje": "..." }</pre>
 *
 * <p>Va con {@code HIGHEST_PRECEDENCE} y acotado al paquete
 * {@code controller.movil} para ganarle al {@code GlobalExceptionHandler} de la
 * web, que responde con otro formato. Que la app reciba siempre la misma forma
 * de error simplifica mucho el cliente.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.usic.SistemasActivosFijosUAP.controller.movil")
public class MovilExceptionHandler {

    @ExceptionHandler(AuthMovilException.class)
    public ResponseEntity<Map<String, Object>> auth(AuthMovilException ex) {
        HttpStatus estado = switch (ex.getCodigo()) {
            case "CREDENCIALES", "REFRESH_INVALIDO"        -> HttpStatus.UNAUTHORIZED;
            case "USUARIO_INACTIVO", "ROL_NO_AUTORIZADO",
                 "SIN_ACCESO_MOVIL"                        -> HttpStatus.FORBIDDEN;
            case "NO_ENCONTRADO"                           -> HttpStatus.NOT_FOUND;
            default                                        -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(estado).body(sobre(ex.getCodigo(), ex.getMessage()));
    }

    /**
     * Permiso {@code MOV_*} ausente. Se atiende aquí porque la excepción se lanza
     * dentro del controlador, y para entonces el filtro de seguridad ya no la ve:
     * sin este manejador acabaría convertida en un 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> sinPermiso(AccessDeniedException ex) {
        log.debug("[MOVIL] Acceso denegado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(sobre("SIN_PERMISO", "No tiene permiso para esta operación"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getDefaultMessage())
                .orElse("Datos inválidos");
        return ResponseEntity.badRequest().body(sobre("DATOS_INVALIDOS", mensaje));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generico(Exception ex) {
        log.error("[MOVIL] Error no controlado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(sobre("ERROR_SERVIDOR", "Ocurrió un error en el servidor"));
    }

    private Map<String, Object> sobre(String codigo, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("codigo", codigo);
        cuerpo.put("mensaje", mensaje);
        return cuerpo;
    }
}
