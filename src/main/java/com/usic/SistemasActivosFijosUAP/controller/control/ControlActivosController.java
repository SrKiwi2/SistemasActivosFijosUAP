package com.usic.SistemasActivosFijosUAP.controller.control;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.dto.control.AbrirLevantamientoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CerrarLevantamientoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.MarcaRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.MarcasLoteRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ResolverHallazgoRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.Inventario;
import com.usic.SistemasActivosFijosUAP.model.entity.InventarioDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ControlActivosService;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Control de Activos por Responsable — cara web.
 *
 * <p>Las vistas son fragmentos SPA: el sidebar trae la URL en {@code data-url} y
 * el shell los inyecta, igual que el resto de los módulos.
 *
 * <p>La autorización va aquí y en el servicio, no en {@code SeguridadConfig}:
 * en este sistema las rutas HTTP están abiertas y el control real es de capa.
 */
@Controller
@RequestMapping("/administracion/control-activos")
@RequiredArgsConstructor
@Slf4j
public class ControlActivosController {

    private final ControlActivosService servicio;

    /** Capacidad para cerrar hallazgos. Sin ella se puede mirar, no resolver. */
    private static final String PERMISO_RESOLVER = "opcion_control_resolver";

    // ── Vistas ───────────────────────────────────────────────────────────────

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vistaMapa() {
        return "controlActivos/mapa";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/faltantes/vista")
    public String vistaFaltantes() {
        return "controlActivos/faltantes";
    }

    // ── Mapa ─────────────────────────────────────────────────────────────────

    @ValidarUsuarioAutenticado
    @GetMapping("/mapa/predios")
    @ResponseBody
    public ResponseEntity<?> predios() {
        return ResponseEntity.ok(servicio.mapaPredios());
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/mapa/predios/{idPredio}/oficinas")
    @ResponseBody
    public ResponseEntity<?> oficinas(@PathVariable Long idPredio) {
        return ResponseEntity.ok(servicio.mapaOficinas(idPredio));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/mapa/oficinas/{idOficina}/responsables")
    @ResponseBody
    public ResponseEntity<?> responsables(@PathVariable Long idOficina) {
        return ResponseEntity.ok(servicio.mapaResponsables(idOficina));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/responsables/{idResponsable}/activos")
    @ResponseBody
    public ResponseEntity<?> activos(@PathVariable Long idResponsable) {
        return ResponseEntity.ok(servicio.activosDe(idResponsable));
    }

    // ── Levantamiento ────────────────────────────────────────────────────────

    @ValidarUsuarioAutenticado
    @PostMapping("/levantamientos")
    @ResponseBody
    public ResponseEntity<?> abrir(@Valid @RequestBody AbrirLevantamientoRequest req,
                                   HttpServletRequest http) {
        Usuario u = usuarioDe(http);
        return ResponseEntity.ok(servicio.abrir(req, u == null ? null : u.getIdUsuario(),
                Inventario.ORIGEN_WEB));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/levantamientos/{id}")
    @ResponseBody
    public ResponseEntity<?> levantamiento(@PathVariable Long id) {
        return ResponseEntity.ok(servicio.obtener(id));
    }

    /**
     * Marca un activo desde la web. Reusa el mismo camino que el lote del móvil
     * enviando un solo elemento: una sola implementación de la regla de marcado
     * evita que web y app terminen discrepando.
     */
    @ValidarUsuarioAutenticado
    @PatchMapping("/levantamientos/{id}/detalle/{idDetalle}")
    @ResponseBody
    public ResponseEntity<?> marcar(@PathVariable Long id,
                                    @PathVariable Long idDetalle,
                                    @RequestBody MarcaRequest cuerpo) {
        MarcaRequest marca = new MarcaRequest(
                idDetalle, null, cuerpo.situacion(), InventarioDetalle.ORIGEN_WEB,
                cuerpo.fecha(), cuerpo.observacion(), cuerpo.idEstadoObservado());
        return ResponseEntity.ok(
                servicio.aplicarMarcas(id, new MarcasLoteRequest(null, List.of(marca))));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/levantamientos/{id}/cerrar")
    @ResponseBody
    public ResponseEntity<?> cerrar(@PathVariable Long id,
                                    @RequestBody(required = false) CerrarLevantamientoRequest req,
                                    HttpServletRequest http) {
        return ResponseEntity.ok(servicio.cerrar(id, req, nombreUsuario(http)));
    }

    // ── Faltantes ────────────────────────────────────────────────────────────

    @ValidarUsuarioAutenticado
    @GetMapping("/faltantes")
    @ResponseBody
    public ResponseEntity<?> faltantes(@RequestParam(required = false) Long idPredio,
                                       @RequestParam(required = false) Long idOficina,
                                       @RequestParam(required = false) Long idResponsable,
                                       @RequestParam(required = false) String tipo,
                                       @RequestParam(required = false) String estado) {
        return ResponseEntity.ok(
                servicio.faltantes(idPredio, idOficina, idResponsable, vacioANull(tipo), vacioANull(estado)));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/hallazgos/{id}/resolver")
    @ResponseBody
    public ResponseEntity<?> resolver(@PathVariable Long id,
                                      @Valid @RequestBody ResolverHallazgoRequest req,
                                      HttpServletRequest http) {
        if (!puedeResolver(http)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "message", "No tiene permiso para resolver hallazgos"));
        }
        servicio.resolver(id, req, nombreUsuario(http));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/hallazgos/{id}/reabrir")
    @ResponseBody
    public ResponseEntity<?> reabrir(@PathVariable Long id, HttpServletRequest http) {
        if (!puedeResolver(http)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "message", "No tiene permiso para reabrir hallazgos"));
        }
        servicio.reabrir(id, nombreUsuario(http));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private Usuario usuarioDe(HttpServletRequest http) {
        Object u = http.getSession().getAttribute("usuario");
        return (u instanceof Usuario usuario) ? usuario : null;
    }

    private String nombreUsuario(HttpServletRequest http) {
        Usuario u = usuarioDe(http);
        return u != null ? u.getUsuario() : "sistema";
    }

    @SuppressWarnings("unchecked")
    private boolean puedeResolver(HttpServletRequest http) {
        Object rol = http.getSession().getAttribute("nombre_rol");
        if ("ADMINISTRADOR".equals(rol) || "SUPER USUARIO".equals(rol)) {
            return true;
        }
        Object opciones = http.getSession().getAttribute("opciones");
        return (opciones instanceof Set<?> set) && ((Set<String>) set).contains(PERMISO_RESOLVER);
    }

    private String vacioANull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /**
     * Los errores de negocio de este módulo son condiciones esperables (cerrar
     * algo ya cerrado, resolver dos veces), no fallos: se devuelven con su
     * mensaje para que la pantalla lo muestre tal cual.
     */
    @ExceptionHandler({ ReglaNegocioException.class,
                        IllegalArgumentException.class, IllegalStateException.class })
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manejarNegocio(RuntimeException e) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manejarError(Exception e) {
        log.error("Error en Control de Activos: {}", e.getMessage(), e);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", false);
        cuerpo.put("message", "No se pudo completar la operación: " + e.getMessage());
        return ResponseEntity.internalServerError().body(cuerpo);
    }
}
