package com.usic.SistemasActivosFijosUAP.controller.movil;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.config.movil.UsuarioMovilPrincipal;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.LoginMovilRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.RefreshMovilRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.SesionMovilResponse;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.UsuarioMovilDTO;
import com.usic.SistemasActivosFijosUAP.model.service.movil.AuthMovilService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Autenticación de la app móvil — {@code /api/movil/auth/**}.
 *
 * <p>Las credenciales son las mismas del sistema web. Lo que cambia es el
 * transporte de la sesión: en vez de una cookie de {@code HttpSession}, un JWT
 * en la cabecera {@code Authorization} (ver
 * {@link com.usic.SistemasActivosFijosUAP.config.movil.MovilSecurityConfig}).
 */
@RestController
@RequestMapping("/api/movil")
@RequiredArgsConstructor
public class AuthMovilController {

    private final AuthMovilService authMovilService;

    @Value("${movil.version.minima}")
    private String versionMinima;

    @Value("${movil.version.actual}")
    private String versionActual;

    // ── Público ──────────────────────────────────────────────────────────────

    @PostMapping("/auth/login")
    public ResponseEntity<SesionMovilResponse> login(@Valid @RequestBody LoginMovilRequest req) {
        return ResponseEntity.ok(authMovilService.login(req));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<SesionMovilResponse> refresh(@Valid @RequestBody RefreshMovilRequest req) {
        return ResponseEntity.ok(authMovilService.refresh(req.refreshToken()));
    }

    /**
     * Versión mínima aceptada. La app la consulta antes de autenticar para
     * avisar al usuario si su APK quedó vieja.
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> version() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("versionMinima", versionMinima);
        cuerpo.put("versionActual", versionActual);
        return ResponseEntity.ok(cuerpo);
    }

    /** Comprobación de alcance del servidor, sin autenticar. */
    @GetMapping("/salud")
    public ResponseEntity<Map<String, Object>> salud() {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("servicio", "SCIAF Móvil");
        cuerpo.put("hora", LocalDateTime.now().toString());
        return ResponseEntity.ok(cuerpo);
    }

    // ── Autenticado ──────────────────────────────────────────────────────────

    /** Perfil y permisos vigentes. La app lo llama en cada arranque con red. */
    @GetMapping("/auth/me")
    public ResponseEntity<UsuarioMovilDTO> me() {
        UsuarioMovilPrincipal principal = UsuarioMovilPrincipal.actual();
        return ResponseEntity.ok(authMovilService.perfilDe(principal.idUsuario()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Map<String, Object>> logout(@Valid @RequestBody RefreshMovilRequest req) {
        authMovilService.logout(req.refreshToken());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("mensaje", "Sesión cerrada");
        return ResponseEntity.ok(cuerpo);
    }

    /** Prueba de extremo a extremo del token (usada en la Fase 0). */
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        UsuarioMovilPrincipal principal = UsuarioMovilPrincipal.actual();
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("usuario", principal.usuario());
        cuerpo.put("rol", principal.rol());
        cuerpo.put("permisos", principal.permisos().size());
        cuerpo.put("hora", LocalDateTime.now().toString());
        return ResponseEntity.ok(cuerpo);
    }
}
