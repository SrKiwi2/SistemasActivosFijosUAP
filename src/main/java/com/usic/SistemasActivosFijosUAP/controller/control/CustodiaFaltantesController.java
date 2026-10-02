package com.usic.SistemasActivosFijosUAP.controller.control;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ActaFaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CustodiaDTOs;
import com.usic.SistemasActivosFijosUAP.model.dto.control.RegistrarFaltantesRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ActaFaltanteService;
import com.usic.SistemasActivosFijosUAP.model.service.control.PdfActaFaltanteService;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReportesCustodiaService;
import com.usic.SistemasActivosFijosUAP.model.service.control.ResolucionFaltanteService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Custodia de faltantes — registro de faltantes por persona, actas y su PDF, resolución
 * (salida de la custodia), reportes y conciliación.
 * <p>
 * Consultar lo puede cualquiera con acceso a Control de Activos. Registrar, anular,
 * reintentar el envío y resolver mueven bienes en el VSIAF: solo administradores o quien
 * tenga el permiso de resolver faltantes (el HTTP está abierto en este sistema; el control
 * es acá).
 */
@RestController
@RequestMapping("/administracion/control-activos/custodia")
@RequiredArgsConstructor
@Slf4j
public class CustodiaFaltantesController {

    private static final String PERMISO_RESOLVER = "opcion_control_resolver";

    private final ActaFaltanteService actaService;
    private final PdfActaFaltanteService pdfService;
    private final ResolucionFaltanteService resolucionService;
    private final ReportesCustodiaService reportesService;

    // ── Consultas ────────────────────────────────────────────────────────────

    @ValidarUsuarioAutenticado
    @GetMapping("/personas")
    public ResponseEntity<?> buscarPersonas(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(actaService.buscarPersonas(q));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/personas/{idPersona}/bienes")
    public ResponseEntity<?> bienes(@PathVariable Long idPersona) {
        return ResponseEntity.ok(actaService.bienesDePersona(idPersona));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/actas")
    public ResponseEntity<?> actas(@RequestParam(required = false) Long idPersona) {
        return ResponseEntity.ok(actaService.actas(idPersona));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/actas/{idActa}/pdf")
    public ResponseEntity<?> pdf(@PathVariable Long idActa) throws Exception {
        ActaFaltanteDTO acta = actaService.porId(idActa);
        byte[] pdf = pdfService.generar(acta);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDisposition(ContentDisposition.inline()
                .filename((acta.esNotificacion() ? "notificacion_faltantes_" : "acta_faltantes_")
                        + acta.numero().replaceAll("[^A-Za-z0-9-]", "-") + ".pdf").build());
        return new ResponseEntity<>(pdf, h, HttpStatus.OK);
    }

    // ── Acciones ─────────────────────────────────────────────────────────────

    @ValidarUsuarioAutenticado
    @PostMapping("/actas")
    public ResponseEntity<?> registrar(@RequestBody RegistrarFaltantesRequest req, HttpServletRequest http) {
        exigirPermiso(http);
        ActaFaltanteService.Registro r = actaService.registrar(req, usuarioDe(http));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("idActa", r.idActa());
        cuerpo.put("numero", r.numero());
        cuerpo.put("total", r.total());
        cuerpo.put("message", r.mensaje());
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Vista previa de la notificación con la persona, los bienes y el plazo elegidos, <b>sin
     * registrar nada</b>: no guarda, no saca número y no toca el VSIAF (ver
     * {@link ActaFaltanteService#vistaPrevia}). Pide el mismo permiso que registrar porque
     * muestra lo que se emitiría.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/actas/vista-previa")
    public ResponseEntity<?> vistaPrevia(@RequestBody RegistrarFaltantesRequest req, HttpServletRequest http)
            throws Exception {
        exigirPermiso(http);
        ActaFaltanteDTO acta = actaService.vistaPrevia(req, usuarioDe(http));
        byte[] pdf = pdfService.generarVistaPrevia(acta);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setCacheControl(CacheControl.noStore());
        h.setContentDisposition(ContentDisposition.inline()
.filename("vista_previa_notificacion_faltantes.pdf").build());
        return new ResponseEntity<>(pdf, h, HttpStatus.OK);
    }

    // ── Notificar plazo (re-emitir notificación con nuevo plazo) ────────────

    @ValidarUsuarioAutenticado
    @PostMapping("/actas/notificar-plazo")
    public ResponseEntity<?> notificarPlazo(@RequestBody CustodiaDTOs.NotificarPlazoRequest req, HttpServletRequest http)
            throws Exception {
        exigirPermiso(http);
        ActaFaltanteService.Registro r = actaService.notificarPlazo(req, usuarioDe(http));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("idActa", r.idActa());
        cuerpo.put("numero", r.numero());
        cuerpo.put("total", r.total());
        cuerpo.put("message", r.mensaje());
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Vista previa de la notificación con el nuevo plazo, <b>sin registrar nada</b>.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/actas/notificar-plazo/vista-previa")
    public ResponseEntity<?> notificarPlazoVistaPrevia(@RequestBody CustodiaDTOs.NotificarPlazoRequest req, HttpServletRequest http)
            throws Exception {
        exigirPermiso(http);
        ActaFaltanteDTO acta = actaService.notificarPlazoVistaPrevia(req, usuarioDe(http));
        byte[] pdf = pdfService.generarVistaPrevia(acta);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setCacheControl(CacheControl.noStore());
        h.setContentDisposition(ContentDisposition.inline()
.filename("vista_previa_notificacion_plazo.pdf").build());
        return new ResponseEntity<>(pdf, h, HttpStatus.OK);
    }

    // ── Regenerar notificación (SIN cambiar plazo) ────────────────────────

    @ValidarUsuarioAutenticado
    @PostMapping("/actas/regenerar-notificacion")
    public ResponseEntity<?> regenerarNotificacion(@RequestBody CustodiaDTOs.RegenerarNotificacionRequest req, HttpServletRequest http)
            throws Exception {
        exigirPermiso(http);
        ActaFaltanteService.Registro r = actaService.regenerarNotificacion(req, usuarioDe(http));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("idActa", r.idActa());
        cuerpo.put("numero", r.numero());
        cuerpo.put("total", r.total());
        cuerpo.put("message", r.mensaje());
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Vista previa de la notificación regenerada, <b>sin registrar nada</b>.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/actas/regenerar-notificacion/vista-previa")
    public ResponseEntity<?> regenerarNotificacionVistaPrevia(@RequestBody CustodiaDTOs.RegenerarNotificacionRequest req, HttpServletRequest http)
            throws Exception {
        exigirPermiso(http);
        ActaFaltanteDTO acta = actaService.regenerarNotificacionVistaPrevia(req, usuarioDe(http));
        byte[] pdf = pdfService.generarVistaPrevia(acta);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setCacheControl(CacheControl.noStore());
        h.setContentDisposition(ContentDisposition.inline()
                .filename("vista_previa_regenerar_notificacion.pdf").build());
        return new ResponseEntity<>(pdf, h, HttpStatus.OK);
    }

    // ── Generar notificación reiterativa ────────────────────────────────────

    @ValidarUsuarioAutenticado
    @PostMapping("/actas/generar-reiterativa")
    public ResponseEntity<?> generarReiterativa(@RequestBody CustodiaDTOs.GenerarReiterativaRequest req, HttpServletRequest http)
            throws Exception {
        exigirPermiso(http);
        ActaFaltanteService.Registro r = actaService.generarReiterativa(req, usuarioDe(http));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("idActa", r.idActa());
        cuerpo.put("numero", r.numero());
        cuerpo.put("total", r.total());
        cuerpo.put("message", r.mensaje());
        return ResponseEntity.ok(cuerpo);
    }

    /**
     * Vista previa de la notificación reiterativa, <b>sin registrar nada</b>.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/actas/generar-reiterativa/vista-previa")
    public ResponseEntity<?> generarReiterativaVistaPrevia(@RequestBody CustodiaDTOs.GenerarReiterativaRequest req, HttpServletRequest http)
            throws Exception {
        exigirPermiso(http);
        ActaFaltanteDTO acta = actaService.generarReiterativaVistaPrevia(req, usuarioDe(http));
        byte[] pdf = pdfService.generarVistaPrevia(acta);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setCacheControl(CacheControl.noStore());
        h.setContentDisposition(ContentDisposition.inline()
                .filename("vista_previa_reiterativa.pdf").build());
        return new ResponseEntity<>(pdf, h, HttpStatus.OK);
    }

    public record AnularRequest(String motivo) {}

    @ValidarUsuarioAutenticado
    @PostMapping("/actas/{idActa}/anular")
    public ResponseEntity<?> anular(@PathVariable Long idActa, @RequestBody AnularRequest req, HttpServletRequest http) {
        exigirPermiso(http);
        actaService.anular(idActa, req != null ? req.motivo() : null, usuarioDe(http));
        return ResponseEntity.ok(Map.of("ok", true, "message", "Acta anulada."));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/actas/{idActa}/reintentar")
    public ResponseEntity<?> reintentar(@PathVariable Long idActa, HttpServletRequest http) {
        exigirPermiso(http);
        int n = actaService.reintentarEnvio(idActa, usuarioDe(http));
        return ResponseEntity.ok(Map.of("ok", true, "message",
                n == 0 ? "No había bienes con error en esta acta." : "Se reintentó el traslado de " + n + " bien(es)."));
    }

    // ── Regularización de históricos ─────────────────────────────────────────

    /** Bienes en las oficinas de faltantes sin acta, por persona, con su origen según el historial. */
    @ValidarUsuarioAutenticado
    @GetMapping("/historicos")
    public ResponseEntity<?> historicos() {
        return ResponseEntity.ok(actaService.historicos());
    }

    /** Registra los históricos elegidos con un acta de regularización (no mueve nada ni toca el VSIAF). */
    @ValidarUsuarioAutenticado
    @PostMapping("/regularizar")
    public ResponseEntity<?> regularizar(@RequestBody CustodiaDTOs.RegularizarRequest req, HttpServletRequest http) {
        exigirPermiso(http);
        ActaFaltanteService.Registro r = actaService.regularizar(req, usuarioDe(http));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("ok", true);
        cuerpo.put("idActa", r.idActa());
        cuerpo.put("numero", r.numero());
        cuerpo.put("total", r.total());
        cuerpo.put("message", r.mensaje());
        return ResponseEntity.ok(cuerpo);
    }

    // ── Resolución (fase 5) ──────────────────────────────────────────────────

    @ValidarUsuarioAutenticado
    @GetMapping("/hallazgos/{idHallazgo}/situacion")
    public ResponseEntity<?> situacion(@PathVariable Long idHallazgo) {
        return ResponseEntity.ok(resolucionService.situacion(idHallazgo));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/hallazgos/{idHallazgo}/destinos")
    public ResponseEntity<?> destinos(@PathVariable Long idHallazgo, @RequestParam(required = false) String q) {
        return ResponseEntity.ok(resolucionService.destinos(idHallazgo, q));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/hallazgos/{idHallazgo}/resolver")
    public ResponseEntity<?> resolver(@PathVariable Long idHallazgo,
                                      @RequestBody ResolucionFaltanteService.Solicitud req,
                                      HttpServletRequest http) {
        exigirPermiso(http);
        String mensaje = resolucionService.resolver(idHallazgo, req, usuarioDe(http));
        return ResponseEntity.ok(Map.of("ok", true, "message", mensaje));
    }

    // ── Reportes y conciliación (fase 6) ─────────────────────────────────────

    @ValidarUsuarioAutenticado
    @GetMapping("/predios")
    public ResponseEntity<?> prediosConCustodia() {
        return ResponseEntity.ok(reportesService.prediosConCustodia());
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/reportes/predio/{idPredio}")
    public ResponseEntity<?> reporteCustodiaPredio(@PathVariable Long idPredio,
                                                   @RequestParam(defaultValue = "pdf") String formato,
                                                   HttpServletRequest http) throws Exception {
        String hoy = java.time.LocalDate.now().toString();
        return "xlsx".equalsIgnoreCase(formato)
                ? archivo(reportesService.custodiaPredioExcel(idPredio), "custodia_predio_" + idPredio + "_" + hoy + ".xlsx", XLSX)
                : archivo(reportesService.custodiaPredioPdf(idPredio, nombreUsuario(http)),
                          "custodia_predio_" + idPredio + "_" + hoy + ".pdf", MediaType.APPLICATION_PDF);
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/reportes/consolidado")
    public ResponseEntity<?> reporteConsolidado(@RequestParam(defaultValue = "pdf") String formato,
                                                @RequestParam(defaultValue = "true") boolean soloPendientes,
                                                HttpServletRequest http) throws Exception {
        String hoy = java.time.LocalDate.now().toString();
        return "xlsx".equalsIgnoreCase(formato)
                ? archivo(reportesService.consolidadoExcel(soloPendientes), "faltantes_por_persona_" + hoy + ".xlsx", XLSX)
                : archivo(reportesService.consolidadoPdf(soloPendientes, nombreUsuario(http)),
                          "faltantes_por_persona_" + hoy + ".pdf", MediaType.APPLICATION_PDF);
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/conciliacion")
    public ResponseEntity<?> conciliacion() {
        return ResponseEntity.ok(reportesService.conciliacion());
    }

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private ResponseEntity<byte[]> archivo(byte[] contenido, String nombre, MediaType tipo) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(tipo);
        h.setContentDisposition((MediaType.APPLICATION_PDF.equals(tipo) ? ContentDisposition.inline()
                : ContentDisposition.attachment()).filename(nombre).build());
        return new ResponseEntity<>(contenido, h, HttpStatus.OK);
    }

    private String nombreUsuario(HttpServletRequest http) {
        Usuario u = usuarioDe(http);
        return u != null ? u.getUsuario() : null;
    }

    // ── Internos ─────────────────────────────────────────────────────────────

    private Usuario usuarioDe(HttpServletRequest http) {
        Object u = http.getSession().getAttribute("usuario");
        return (u instanceof Usuario usuario) ? usuario : null;
    }

    @SuppressWarnings("unchecked")
    private void exigirPermiso(HttpServletRequest http) {
        Object rol = http.getSession().getAttribute("nombre_rol");
        if ("ADMINISTRADOR".equals(rol) || "SUPER USUARIO".equals(rol)) return;
        Object opciones = http.getSession().getAttribute("opciones");
        if ((opciones instanceof Set<?> set) && ((Set<String>) set).contains(PERMISO_RESOLVER)) return;
        throw new SinPermisoException();
    }

    private static final class SinPermisoException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    @ExceptionHandler(SinPermisoException.class)
    public ResponseEntity<Map<String, Object>> sinPermiso() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                "message", "No tiene permiso para registrar faltantes ni mover bienes a la custodia."));
    }

    /** Dos personas registraron el mismo bien a la vez: el índice uk_hall_faltante_pendiente frenó al segundo. */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> choque() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false, "message",
                "Otro usuario registró alguno de estos bienes al mismo tiempo. No se registró nada: "
                + "vuelva a cargar la lista y revise."));
    }

    @ExceptionHandler({ ReglaNegocioException.class, IllegalArgumentException.class })
    public ResponseEntity<Map<String, Object>> negocio(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("ok", false, "message", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> error(Exception e) {
        log.error("[CUSTODIA] Error: {}", e.getMessage(), e);
        return ResponseEntity.internalServerError().body(Map.of("ok", false,
                "message", "No se pudo completar la operación: " + e.getMessage()));
    }
}
