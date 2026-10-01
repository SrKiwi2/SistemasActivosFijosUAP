package com.usic.SistemasActivosFijosUAP.controller.supervision;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.GestionUsuariosService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.PresenciaService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Supervisión → Usuarios conectados: quién tiene el sistema abierto, qué pantalla está
 * mirando, qué pestañas tiene abiertas y por dónde anduvo. Solo ADMINISTRADOR y SUPER
 * USUARIO (mismo criterio que el monitoreo de actividad).
 *
 * <p>{@code POST /api/presencia} es el aviso que manda cada navegador con sesión
 * (sciaf-presencia.js); no exige permiso de menú porque lo mandan todos.
 */
@Controller
public class ConectadosController {

    private final PresenciaService presencia;
    private final GestionUsuariosService gestion;

    public ConectadosController(PresenciaService presencia, GestionUsuariosService gestion) {
        this.presencia = presencia;
        this.gestion = gestion;
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/administracion/conectados/vista")
    public String vista(HttpServletRequest request) {
        if (!RolesSciaf.esAdministrativo(request)) return "supervision/sin_permiso";
        return "supervision/conectados";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/administracion/conectados/datos")
    @ResponseBody
    public ResponseEntity<?> datos(HttpServletRequest request) {
        if (!RolesSciaf.esAdministrativo(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        List<Map<String, Object>> lista = presencia.listar();
        Long yo = RolesSciaf.usuarioDe(request).getIdUsuario();
        lista.forEach(m -> m.put("esYo", yo.equals(m.get("idUsuario"))));
        return ResponseEntity.ok(Map.of("ok", true, "sesiones", lista));
    }

    /** Cierra todas las sesiones de ese usuario (las mismas reglas que en Usuarios). */
    @ValidarUsuarioAutenticado
    @PostMapping("/administracion/conectados/cerrar/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cerrar(HttpServletRequest request, @PathVariable("id") String id) {
        if (!RolesSciaf.esAdministrativo(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "msg", "Sin permiso."));
        }
        Long idUsuario = presencia.usuarioDe(id);
        if (idUsuario == null) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "Esa sesión ya no está conectada."));
        }
        try {
            gestion.cerrarSesiones(RolesSciaf.usuarioDe(request), idUsuario);
            return ResponseEntity.ok(Map.of("ok", true, "msg", "Se cerraron sus sesiones abiertas"));
        } catch (ReglaNegocioException e) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    /** Aviso de presencia de cada navegador con sesión. Sin sesión: 401 (no se crea una). */
    @PostMapping("/api/presencia")
    @ResponseBody
    public void aviso(HttpServletRequest request, HttpServletResponse response,
            @RequestBody PresenciaService.Aviso aviso) {
        if (request.getSession(false) == null || RolesSciaf.usuarioDe(request) == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        presencia.registrar(request, aviso);
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
