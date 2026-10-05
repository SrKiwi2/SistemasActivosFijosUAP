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
 *
 * <p>Carga eficiente: la vista llega con la primera página y el resumen del día ya
 * armados (un solo pedido al abrir), y cada refresco trae página + resumen en la misma
 * respuesta ({@code resumen=true}) en vez de dos pedidos.
 */
@Controller
@RequestMapping("/administracion/actividad")
@RequiredArgsConstructor
public class ActividadController {

    /** Filas que viajan con la vista: alcanzan para la primera página en cualquier pantalla. */
    private static final int FILAS_INICIALES = 50;

    private final IActividadSistemaDao dao;
    private final AutorizacionService autorizacionService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista(Model model, HttpServletRequest request) {
        if (!RolesSciaf.esAdministrativo(request)) return "supervision/sin_permiso";
        model.addAttribute("usuarios", dao.usuariosConActividad());
        model.addAttribute("inicial", pagina(1, 0, FILAS_INICIALES, null, null, null, null, null, null, true));
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
            @RequestParam(required = false) String hasta,
            @RequestParam(defaultValue = "false") boolean resumen) {
        if (!RolesSciaf.esAdministrativo(request)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(pagina(draw, start, length, usuario, modulo, accion, texto, desde, hasta, resumen));
    }

    /** Tarjetas de resumen del día: por acción, usuarios más activos y solicitudes pendientes. */
    @ValidarUsuarioAutenticado
    @GetMapping("/api/resumen")
    @ResponseBody
    public ResponseEntity<?> resumen(HttpServletRequest request) {
        if (!RolesSciaf.esAdministrativo(request)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(resumenDelDia());
    }

    /** Una página en formato DataTables; con {@code conResumen} agrega el resumen del día. */
    private Map<String, Object> pagina(int draw, int start, int length, String usuario, String modulo,
            String accion, String texto, String desde, String hasta, boolean conResumen) {
        int size = Math.min(Math.max(length, 1), 200);
        boolean sinFiltros = vacio(usuario) && vacio(modulo) && vacio(accion) && vacio(texto) && vacio(desde) && vacio(hasta);
        Specification<ActividadSistema> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!vacio(usuario)) p.add(cb.equal(root.get("usuario"), usuario));
            if (!vacio(modulo)) p.add(cb.equal(root.get("modulo"), modulo));
            if (!vacio(accion)) p.add(cb.equal(root.get("accion"), accion));
            if (!vacio(texto)) {
                String like = "%" + texto.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("referencia")), like),
                            cb.like(cb.lower(root.get("descripcion")), like)));
            }
            if (!vacio(desde)) p.add(cb.greaterThanOrEqualTo(root.get("fecha"), LocalDate.parse(desde).atStartOfDay()));
            if (!vacio(hasta)) p.add(cb.lessThan(root.get("fecha"), LocalDate.parse(hasta).plusDays(1).atStartOfDay()));
            return cb.and(p.toArray(new Predicate[0]));
        };
        Page<ActividadSistema> page = dao.findAll(spec,
                PageRequest.of(Math.max(start, 0) / size, size, Sort.by(Sort.Direction.DESC, "idActividad")));

        Map<String, Object> res = new HashMap<>();
        res.put("draw", draw);
        // Sin filtros el total ya viene en la página: no hace falta contar la tabla otra vez.
        res.put("recordsTotal", sinFiltros ? page.getTotalElements() : dao.count());
        res.put("recordsFiltered", page.getTotalElements());
        res.put("data", page.getContent().stream().map(ActividadService::aMapa).toList());
        if (conResumen) res.put("resumen", resumenDelDia());
        return res;
    }

    private Map<String, Object> resumenDelDia() {
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
        return Map.of(
                "totalHoy", total,
                "porAccion", porAccion,
                "topUsuarios", top,
                "solicitudesPendientes", autorizacionService.contarPendientes());
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }
}
