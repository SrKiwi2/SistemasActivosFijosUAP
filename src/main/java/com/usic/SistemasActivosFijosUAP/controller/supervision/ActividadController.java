package com.usic.SistemasActivosFijosUAP.controller.supervision;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.dao.IActividadSistemaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ActividadSistema;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.AutorizacionService;

import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Monitoreo de actividad: solo lectura, solo ADMINISTRADOR y SUPER USUARIO. La tabla se
 * refresca sola con el evento SSE {@code actividad}.
 */
@Controller
@RequestMapping("/administracion/actividad")
@RequiredArgsConstructor
public class ActividadController {

    private final IActividadSistemaDao dao;
    private final AutorizacionService autorizacionService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista(Model model, HttpServletRequest request) {
        if (!RolesSciaf.esAdministrativo(request)) return "supervision/sin_permiso";
        model.addAttribute("usuarios", dao.usuariosConActividad());
        return "supervision/actividad";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/datatables")
    @ResponseBody
    public ResponseEntity<?> datatables(HttpServletRequest request,
            @RequestParam(defaultValue = "1") int draw,
            @RequestParam(defaultValue = "0") int start,
            @RequestParam(defaultValue = "25") int length,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String texto,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta) {
        if (!RolesSciaf.esAdministrativo(request)) return ResponseEntity.status(403).build();

        int size = Math.min(Math.max(length, 1), 200);
        Specification<ActividadSistema> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (vacio(usuario) == false) p.add(cb.equal(root.get("usuario"), usuario));
            if (vacio(modulo) == false) p.add(cb.equal(root.get("modulo"), modulo));
            if (vacio(accion) == false) p.add(cb.equal(root.get("accion"), accion));
            if (vacio(texto) == false) {
                String like = "%" + texto.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("referencia")), like),
                            cb.like(cb.lower(root.get("descripcion")), like)));
            }
            if (vacio(desde) == false) p.add(cb.greaterThanOrEqualTo(root.get("fecha"), LocalDate.parse(desde).atStartOfDay()));
            if (vacio(hasta) == false) p.add(cb.lessThan(root.get("fecha"), LocalDate.parse(hasta).plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<ActividadSistema> page = dao.findAll(spec,
                PageRequest.of(Math.max(start, 0) / size, size, Sort.by(Sort.Direction.DESC, "idActividad")));

        List<Map<String, Object>> data = page.getContent().stream().map(ActividadService::aMapa).toList();
        Map<String, Object> res = new HashMap<>();
        res.put("draw", draw);
        res.put("recordsTotal", dao.count());
        res.put("recordsFiltered", page.getTotalElements());
        res.put("data", data);
        return ResponseEntity.ok(res);
    }

    /** Tarjetas de resumen del día: por acción, usuarios más activos y solicitudes pendientes. */
    @ValidarUsuarioAutenticado
    @GetMapping("/api/resumen")
    @ResponseBody
    public ResponseEntity<?> resumen(HttpServletRequest request) {
        if (!RolesSciaf.esAdministrativo(request)) return ResponseEntity.status(403).build();
        LocalDateTime hoy = LocalDate.now().atStartOfDay();

        Map<String, Long> porAccion = new LinkedHashMap<>();
        long total = 0;
        for (Object[] f : dao.contarPorAccionDesde(hoy)) {
            long n = ((Number) f[1]).longValue();
            porAccion.put((String) f[0], n);
            total += n;
        }
        List<Map<String, Object>> top = new ArrayList<>();
        for (Object[] f : dao.usuariosMasActivosDesde(hoy)) {
            if (top.size() >= 5) break;
            top.add(Map.of("usuario", f[0], "total", ((Number) f[1]).longValue()));
        }
        return ResponseEntity.ok(Map.of(
                "totalHoy", total,
                "porAccion", porAccion,
                "topUsuarios", top,
                "solicitudesPendientes", autorizacionService.contarPendientes()));
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
