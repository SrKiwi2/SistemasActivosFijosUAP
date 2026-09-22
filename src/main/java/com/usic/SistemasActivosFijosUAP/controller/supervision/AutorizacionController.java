package com.usic.SistemasActivosFijosUAP.controller.supervision;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.SolicitudAutorizacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.AutorizacionService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Bandeja de autorizaciones. La ven ADMINISTRADOR y SUPER USUARIO; aprueban o rechazan
 * el ADMINISTRADOR y el SUPER USUARIO designado como revisor. Todo por AJAX y con
 * refresco en vivo (evento SSE {@code autorizacion}).
 */
@Controller
@RequestMapping("/administracion/autorizaciones")
@RequiredArgsConstructor
public class AutorizacionController {

    private final AutorizacionService autorizacionService;
    private final IUsuarioDao usuarioDao;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista(Model model, HttpServletRequest request) {
        Usuario u = RolesSciaf.usuarioDe(request);
        if (!RolesSciaf.esAdministrativo(u)) return "supervision/sin_permiso";
        Usuario revisor = autorizacionService.revisor();
        model.addAttribute("puedeRevisar", autorizacionService.puedeRevisar(u));
        model.addAttribute("esAdministrador", RolesSciaf.esAdministrador(u));
        model.addAttribute("revisor", revisor);
        model.addAttribute("superUsuarios", usuarioDao.findAll().stream()
                .filter(x -> RolesSciaf.SUPER_USUARIO.equals(RolesSciaf.rolDe(x)))
                .filter(x -> !"ELIMINADO".equals(x.getEstado()) && !"INACTIVO".equals(x.getEstado()))
                .toList());
        return "supervision/autorizaciones";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/listado")
    @ResponseBody
    public ResponseEntity<?> listado(HttpServletRequest request) {
        if (!RolesSciaf.esAdministrativo(request)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of(
                "pendientes", autorizacionService.pendientes().stream().map(autorizacionService::aMapa).toList(),
                "historial", autorizacionService.historial(100).stream().map(autorizacionService::aMapa).toList()));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/{id}/aprobar")
    @ResponseBody
    public ResponseEntity<?> aprobar(HttpServletRequest request, @PathVariable Long id,
            @RequestParam(required = false) String comentario) {
        return resolver(() -> autorizacionService.aprobar(id, comentario, RolesSciaf.usuarioDe(request)));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/{id}/rechazar")
    @ResponseBody
    public ResponseEntity<?> rechazar(HttpServletRequest request, @PathVariable Long id,
            @RequestParam(required = false) String comentario) {
        return resolver(() -> autorizacionService.rechazar(id, comentario, RolesSciaf.usuarioDe(request)));
    }

    /** El solicitante retira su propia solicitud (cualquier rol). */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/{id}/anular")
    @ResponseBody
    public ResponseEntity<?> anular(HttpServletRequest request, @PathVariable Long id) {
        return resolver(() -> autorizacionService.anular(id, RolesSciaf.usuarioDe(request)));
    }

    /** Solo el ADMINISTRADOR designa (o quita, con idUsuario vacío) al revisor. */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/revisor")
    @ResponseBody
    public ResponseEntity<?> designarRevisor(HttpServletRequest request,
            @RequestParam(required = false) Long idUsuario) {
        try {
            autorizacionService.designarRevisor(idUsuario, RolesSciaf.usuarioDe(request));
            return ResponseEntity.ok(Map.of("ok", true, "msg", idUsuario != null
                    ? "Revisor designado. Desde ahora recibe las solicitudes."
                    : "Sin revisor designado: las solicitudes las verá solo el administrador."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    private ResponseEntity<?> resolver(java.util.function.Supplier<SolicitudAutorizacion> accion) {
        try {
            SolicitudAutorizacion s = accion.get();
            return ResponseEntity.ok(Map.of("ok", true, "estado", s.getEstado(),
                    "msg", s.getRespuesta() != null ? s.getRespuesta() : "Listo.",
                    "solicitud", autorizacionService.aMapa(s)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }
}
