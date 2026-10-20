package com.usic.SistemasActivosFijosUAP.controller.duplicados;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.dto.duplicados.DuplicadosDTOs;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.duplicados.DuplicadosService;
import com.usic.SistemasActivosFijosUAP.model.service.duplicados.PdfNotificacionDuplicadoService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Activos duplicados — el mismo bien registrado dos veces en el VSIAF (error del revalúo).
 * Registrar manda el código que sobra a la oficina de duplicados de su predio y emite la
 * notificación; retirar lo saca con justificación; anular deshace un registro hecho por error.
 * <p>
 * Lo pueden usar los administradores (ADMINISTRADOR, SUPER USUARIO) y quien tenga la opción
 * «Activos duplicados» (p. ej. el usuario de revalúos). El HTTP está abierto en este sistema:
 * el control es acá.
 */
@Slf4j
@Controller
@RequestMapping("/administracion/duplicados")
@RequiredArgsConstructor
public class DuplicadosController {

    public static final String OPCION = "opcion_duplicados";

    private final DuplicadosService service;
    private final PdfNotificacionDuplicadoService pdfService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista(Model model, HttpServletRequest request) {
        if (!puede(request)) return "supervision/sin_permiso";
        Map<String, Object> inicial = new LinkedHashMap<>();
        inicial.put("totales", service.totales());
        model.addAttribute("inicial", inicial);
        return "duplicados/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/datatables")
    @ResponseBody
    public ResponseEntity<?> datatables(@RequestParam(name = "draw", defaultValue = "1") int draw,
                                        @RequestParam(name = "start", defaultValue = "0") int start,
                                        @RequestParam(name = "length", defaultValue = "25") int length,
                                        @RequestParam(name = "search[value]", required = false) String q,
                                        @RequestParam(name = "estado", required = false) String estado,
                                        HttpServletRequest request) {
        exigir(request);
        return ResponseEntity.ok(service.tabla(draw, start, length, q, estado));
    }

    /** Ficha de un activo por su código (para elegir el que sobra y el que se queda). */
    @ValidarUsuarioAutenticado
    @GetMapping("/api/activo")
    @ResponseBody
    public ResponseEntity<?> activo(@RequestParam String codigo, HttpServletRequest request) {
        exigir(request);
        return ResponseEntity.ok(service.fichaPorCodigo(codigo));
    }

    /** Activos parecidos (solo sugerencias: el sistema nunca afirma que son el mismo bien). */
    @ValidarUsuarioAutenticado
    @GetMapping("/api/activo/{id}/gemelos")
    @ResponseBody
    public ResponseEntity<?> gemelos(@PathVariable Long id, HttpServletRequest request) {
        exigir(request);
        return ResponseEntity.ok(service.gemelos(id));
    }

    /** Qué pasaría al registrar, sin tocar nada. */
    @ValidarUsuarioAutenticado
    @GetMapping("/api/situacion")
    @ResponseBody
    public ResponseEntity<?> situacion(@RequestParam Long sobrante, @RequestParam(required = false) Long principal,
                                       HttpServletRequest request) {
        exigir(request);
        return ResponseEntity.ok(service.situacion(sobrante, principal));
    }

    /** La notificación como quedaría, sin registrar nada (sin número ni QR). */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/vista-previa")
    @ResponseBody
    public ResponseEntity<?> vistaPrevia(@RequestBody DuplicadosDTOs.Registro req, HttpServletRequest request) throws Exception {
        exigir(request);
        byte[] pdf = pdfService.generar(service.vistaPrevia(req, RolesSciaf.usuarioDe(request)), true);
        return pdf(pdf, "vista_previa_duplicado.pdf");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/registrar")
    @ResponseBody
    public ResponseEntity<?> registrar(@RequestBody DuplicadosDTOs.Registro req, HttpServletRequest request) {
        exigir(request);
        DuplicadosService.Registrado r = service.registrar(req, RolesSciaf.usuarioDe(request));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        m.put("idDuplicado", r.idDuplicado());
        m.put("numero", r.numero());
        m.put("msg", r.mensaje());
        return ResponseEntity.ok(m);
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/caso/{id}")
    @ResponseBody
    public ResponseEntity<?> caso(@PathVariable Long id, HttpServletRequest request) {
        exigir(request);
        return ResponseEntity.ok(service.detalle(id));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/caso/{id}/pdf")
    @ResponseBody
    public ResponseEntity<?> pdfCaso(@PathVariable Long id, HttpServletRequest request) throws Exception {
        exigir(request);
        DuplicadosDTOs.Notificacion n = service.notificacionDelCaso(id);
        return pdf(pdfService.generar(n, false), "notificacion_duplicado_" + n.numero().replaceAll("[^A-Za-z0-9-]", "-") + ".pdf");
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/caso/{id}/destinos")
    @ResponseBody
    public ResponseEntity<?> destinos(@PathVariable Long id, @RequestParam(required = false) String q, HttpServletRequest request) {
        exigir(request);
        return ResponseEntity.ok(service.destinos(id, q));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/caso/{id}/retirar")
    @ResponseBody
    public ResponseEntity<?> retirar(@PathVariable Long id, @RequestBody DuplicadosDTOs.Retiro req, HttpServletRequest request) {
        exigir(request);
        return ResponseEntity.ok(Map.of("ok", true, "msg", service.retirar(id, req, RolesSciaf.usuarioDe(request))));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/caso/{id}/anular")
    @ResponseBody
    public ResponseEntity<?> anular(@PathVariable Long id, @RequestBody DuplicadosDTOs.Retiro req, HttpServletRequest request) {
        exigir(request);
        return ResponseEntity.ok(Map.of("ok", true, "msg",
                service.anular(id, req != null ? req.justificacion() : null, RolesSciaf.usuarioDe(request))));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/caso/{id}/reintentar")
    @ResponseBody
    public ResponseEntity<?> reintentar(@PathVariable Long id, HttpServletRequest request) {
        exigir(request);
        service.reintentar(id, RolesSciaf.usuarioDe(request));
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Traslado reintentado. Revise el estado en unos segundos."));
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private static ResponseEntity<byte[]> pdf(byte[] pdf, String nombre) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDisposition(ContentDisposition.inline().filename(nombre).build());
        return new ResponseEntity<>(pdf, h, HttpStatus.OK);
    }

    /** Administradores, o quien tenga la opción del módulo. */
    @SuppressWarnings("unchecked")
    static boolean puede(HttpServletRequest request) {
        Usuario u = RolesSciaf.usuarioDe(request);
        if (u == null) return false;
        Object rol = request.getSession().getAttribute("nombre_rol");
        if (RolesSciaf.ADMINISTRADOR.equals(rol) || RolesSciaf.SUPER_USUARIO.equals(rol) || RolesSciaf.esAdministrativo(u)) {
            return true;
        }
        Object opciones = request.getSession().getAttribute("opciones");
        return (opciones instanceof Set<?> set) && ((Set<String>) set).contains(OPCION);
    }

    private static void exigir(HttpServletRequest request) {
        if (!puede(request)) throw new SinPermisoException();
    }

    private static final class SinPermisoException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @ExceptionHandler(SinPermisoException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sinPermiso() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                "msg", "No tiene permiso para Activos duplicados (opción «Activos duplicados»)."));
    }

    /** Dos personas registraron el mismo activo a la vez: el índice uk_dup_activo_pendiente frenó al segundo. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> choque(org.springframework.dao.DataIntegrityViolationException e) {
        log.warn("[DUPLICADOS] La base rechazó la operación: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false, "msg",
                "Otro usuario registró o cambió este activo al mismo tiempo. No se guardó nada: vuelva a cargar y revise."));
    }

    @ExceptionHandler({ ReglaNegocioException.class, IllegalArgumentException.class })
    @ResponseBody
    public ResponseEntity<Map<String, Object>> negocio(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> error(Exception e) {
        log.error("[DUPLICADOS] Error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of("ok", false,
                "msg", "No se pudo completar la operación: " + e.getMessage()));
    }
}
