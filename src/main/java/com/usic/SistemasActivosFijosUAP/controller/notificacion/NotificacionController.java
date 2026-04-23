package com.usic.SistemasActivosFijosUAP.controller.notificacion;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.INotificacionService;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
@Slf4j
public class NotificacionController {
    private final INotificacionService notificacionService;

    // ── Obtener no leídas del usuario conectado ───────────────────────────────
    @GetMapping("/no-leidas")
    public ResponseEntity<?> noLeidas(HttpServletRequest request) {
        Usuario usuario = getUsuario(request);
        if (usuario == null) return ResponseEntity.status(401).build();

        List<Notificacion> lista = notificacionService.obtenerNoLeidas(usuario);

        List<Map<String, Object>> resultado = lista.stream()
            .map(this::toMap)
            .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
            "total",          resultado.size(),
            "notificaciones", resultado
        ));
    }

    // ── Conteo no leídas (para el badge de la campana) ────────────────────────
    @GetMapping("/conteo")
    public ResponseEntity<?> conteo(HttpServletRequest request) {
        Usuario usuario = getUsuario(request);
        if (usuario == null) return ResponseEntity.ok(Map.of("noLeidas", 0));

        long count = notificacionService.contarNoLeidas(usuario);
        return ResponseEntity.ok(Map.of("noLeidas", count));
    }

    // ── Historial paginado ────────────────────────────────────────────────────
    @GetMapping("/historial")
    public ResponseEntity<?> historial(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Usuario usuario = getUsuario(request);
        if (usuario == null) return ResponseEntity.status(401).build();

        Page<Notificacion> pagina = notificacionService.obtenerHistorial(
            usuario, PageRequest.of(page, size));

        return ResponseEntity.ok(Map.of(
            "total",          pagina.getTotalElements(),
            "totalPaginas",   pagina.getTotalPages(),
            "paginaActual",   page,
            "notificaciones", pagina.getContent().stream()
                                .map(this::toMap).collect(Collectors.toList())
        ));
    }

    // ── Marcar una como leída ─────────────────────────────────────────────────
    @PostMapping("/{id}/leer")
    public ResponseEntity<?> marcarLeida(
            @PathVariable Long id,
            HttpServletRequest request) {

        Usuario usuario = getUsuario(request);
        if (usuario == null) return ResponseEntity.status(401).build();

        try {
            notificacionService.marcarLeida(id, usuario);
            long restantes = notificacionService.contarNoLeidas(usuario);
            return ResponseEntity.ok(Map.of("ok", true, "noLeidasRestantes", restantes));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    // ── Marcar TODAS como leídas ──────────────────────────────────────────────
    @PostMapping("/leer-todas")
    public ResponseEntity<?> marcarTodasLeidas(HttpServletRequest request) {
        Usuario usuario = getUsuario(request);
        if (usuario == null) return ResponseEntity.status(401).build();

        int marcadas = notificacionService.marcarTodasLeidas(usuario);
        return ResponseEntity.ok(Map.of(
            "ok",      true,
            "marcadas", marcadas,
            "noLeidasRestantes", 0
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private Usuario getUsuario(HttpServletRequest request) {
        return (Usuario) request.getSession().getAttribute("usuario");
    }

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private Map<String, Object> toMap(Notificacion n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",           n.getIdNotificacion());
        m.put("tipo",         n.getTipo().name());
        m.put("titulo",       n.getTitulo());
        m.put("mensaje",      n.getMensaje());
        m.put("referenciaId", n.getReferenciaId());
        m.put("urlDestino",   n.getUrlDestino());
        m.put("leida",        n.isLeida());
        m.put("fechaCreacion",n.getFechaCreacion() != null
            ? n.getFechaCreacion().format(FMT) : null);
        m.put("fechaLectura", n.getFechaLectura() != null
            ? n.getFechaLectura().format(FMT)  : null);
        return m;
    }
}
