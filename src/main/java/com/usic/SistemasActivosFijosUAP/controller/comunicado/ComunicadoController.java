package com.usic.SistemasActivosFijosUAP.controller.comunicado;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.INotificacionService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.ComunicadoDetalleDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Comunicado;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Módulo administrador/responsable: redactar y enviar comunicados, y controlar
 * quién recibió y quién confirmó la lectura.
 *
 * El acceso a la URL lo gobierna el {@code PermisoOpcionInterceptor} (código
 * {@code opcion_comunicados}); aquí se refuerza con un guard de rol como capa
 * autoritativa de autorización.
 */
@Controller
@RequestMapping("/administracion/comunicados")
@RequiredArgsConstructor
@Slf4j
public class ComunicadoController {

    private final INotificacionService notificacionService;
    private final IUsuarioDao usuarioDao;
    private final IOficinaDao oficinaDao;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Set<String> ROLES_EMISORES =
        Set.of("SUPER USUARIO", "ADMINISTRADOR", "RESPONSABLE");

    // ── Página principal ──────────────────────────────────────────────────────
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista() {
        return "comunicado/vista";
    }

    // ── Tabla de comunicados enviados con estadísticas ────────────────────────
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla")
    public String tabla(Model model) {
        Page<Comunicado> pagina = notificacionService.listarComunicados(
            PageRequest.of(0, 200));

        List<Comunicado> coms = pagina.getContent();
        Map<Long, long[]> resumen = notificacionService.resumenComunicadoLote(
            coms.stream().map(Comunicado::getIdComunicado).collect(Collectors.toList()));

        List<Map<String, Object>> filas = new ArrayList<>();
        for (Comunicado c : coms) {
            long[] r = resumen.getOrDefault(c.getIdComunicado(), new long[]{0, 0});
            long entregadas = r[0];
            long leidas     = r[1];
            int total       = c.getTotalDestinatarios();
            int pct         = total > 0 ? (int) Math.round(leidas * 100.0 / total) : 0;

            Map<String, Object> f = new LinkedHashMap<>();
            f.put("id",             c.getIdComunicado());
            f.put("fecha",          c.getFechaEnvio() != null ? c.getFechaEnvio().format(FMT) : "");
            f.put("tipo",           c.getTipo() != null ? c.getTipo().name() : "GENERAL");
            f.put("titulo",         c.getTitulo());
            f.put("importante",     c.isImportante());
            f.put("alcance",        c.getAlcance() != null ? c.getAlcance().name() : "");
            f.put("alcanceDetalle", c.getAlcanceDetalle());
            f.put("total",          total);
            f.put("recibieron",     entregadas);
            f.put("leyeron",        leidas);
            f.put("pct",            pct);
            filas.add(f);
        }

        model.addAttribute("comunicados", filas);
        return "comunicado/tabla";
    }

    // ── Formulario de redacción ───────────────────────────────────────────────
    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model) {
        List<Map<String, Object>> usuarios = usuarioDao.listarConPersona().stream()
            .map(u -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", u.getIdUsuario());
                String nombre = (u.getPersona() != null)
                    ? u.getPersona().getNombreCompleto() : u.getUsuario();
                m.put("nombre", nombre);
                m.put("usuario", u.getUsuario());
                m.put("rol", u.getRol() != null ? u.getRol().getNombre() : "");
                return m;
            })
            .collect(Collectors.toList());

        List<Map<String, Object>> oficinas = oficinaDao.listarOficinas().stream()
            .map(o -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", o.getIdOficina());
                m.put("nombre", o.getNombre());
                return m;
            })
            .collect(Collectors.toList());

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("oficinas", oficinas);
        return "comunicado/formulario";
    }

    // ── Enviar comunicado ─────────────────────────────────────────────────────
    @ValidarUsuarioAutenticado
    @PostMapping("/enviar")
    public ResponseEntity<Map<String, Object>> enviar(
            @RequestParam("tipo") String tipo,
            @RequestParam("titulo") String titulo,
            @RequestParam(value = "mensaje", required = false) String mensaje,
            @RequestParam(value = "urlDestino", required = false) String urlDestino,
            @RequestParam(value = "importante", defaultValue = "false") boolean importante,
            @RequestParam("alcance") String alcance,
            @RequestParam(value = "idsUsuarios", required = false) List<Long> idsUsuarios,
            @RequestParam(value = "idOficina", required = false) Long idOficina,
            HttpServletRequest request) {

        Map<String, Object> resp = new HashMap<>();

        Usuario emisor = getUsuario(request);
        if (emisor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("ok", false, "msg", "Sesión expirada."));
        }
        if (!esEmisorAutorizado(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("ok", false, "msg", "No tiene permiso para enviar comunicados."));
        }

        if (titulo == null || titulo.isBlank()) {
            resp.put("ok", false);
            resp.put("msg", "El título es obligatorio.");
            return ResponseEntity.ok(resp);
        }

        try {
            Notificacion.TipoNotificacion tipoNotif = parseTipo(tipo);
            Comunicado.Alcance alcanceEnum = Comunicado.Alcance.valueOf(alcance.toUpperCase());

            Set<Long> ids = (idsUsuarios != null) ? new HashSet<>(idsUsuarios) : Set.of();

            String url = (urlDestino != null && !urlDestino.isBlank()) ? urlDestino.trim() : null;

            Comunicado com = notificacionService.enviarComunicado(
                emisor, tipoNotif, titulo.trim(), mensaje, url, importante,
                alcanceEnum, ids, idOficina);

            resp.put("ok", true);
            resp.put("msg", "Comunicado enviado a " + com.getTotalDestinatarios() + " destinatario(s).");
            resp.put("idComunicado", com.getIdComunicado());
            resp.put("totalDestinatarios", com.getTotalDestinatarios());
            return ResponseEntity.ok(resp);

        } catch (IllegalArgumentException | IllegalStateException e) {
            resp.put("ok", false);
            resp.put("msg", e.getMessage());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error enviando comunicado", e);
            resp.put("ok", false);
            resp.put("msg", "Error al enviar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resp);
        }
    }

    // ── Detalle de acuses por destinatario ────────────────────────────────────
    @ValidarUsuarioAutenticado
    @PostMapping("/detalle/{id}")
    public String detalle(@PathVariable("id") Long id, Model model) {
        ComunicadoDetalleDto detalle = notificacionService.detalleComunicado(id);
        model.addAttribute("detalle", detalle);
        return "comunicado/detalle";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Usuario getUsuario(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        return s != null ? (Usuario) s.getAttribute("usuario") : null;
    }

    private boolean esEmisorAutorizado(HttpServletRequest request) {
        HttpSession s = request.getSession(false);
        Object rol = s != null ? s.getAttribute("nombre_rol") : null;
        return rol != null && ROLES_EMISORES.contains(rol.toString().toUpperCase());
    }

    private Notificacion.TipoNotificacion parseTipo(String tipo) {
        try {
            return Notificacion.TipoNotificacion.valueOf(tipo.toUpperCase());
        } catch (Exception e) {
            return Notificacion.TipoNotificacion.GENERAL;
        }
    }
}
