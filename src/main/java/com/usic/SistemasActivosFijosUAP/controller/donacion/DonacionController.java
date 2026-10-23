package com.usic.SistemasActivosFijosUAP.controller.donacion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.dto.donacion.DonacionRegistroRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.entity.ReservaCorrelativo;
import com.usic.SistemasActivosFijosUAP.model.service.ClienteReserva;
import com.usic.SistemasActivosFijosUAP.model.service.donacion.DonacionService;
import com.usic.SistemasActivosFijosUAP.model.service.donacion.WordActaDonacionService;
import com.usic.SistemasActivosFijosUAP.model.service.documento.WordDocumentos;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Administración de activos → Registro de donaciones.
 * <p>
 * Bienes donados con correlativo D ({@code MUN-PRED-GRP-D00001}) que existen solo en el
 * SCIAF. La pantalla sigue la plantilla de módulos (docs/PLANTILLA_MODULOS.md): llega con
 * los catálogos y la primera página de actas, y registra en un solo pedido JSON
 * {@code { ok, msg, … }}. Ver {@link DonacionService}.
 * <p>
 * Permiso: la opción de menú {@code opcion_donacion} (o ser ADMINISTRADOR / SUPER USUARIO).
 * El interceptor de menú ya cierra la ruta; aquí se repite porque casi todo
 * {@code /administracion/**} es {@code permitAll()} en la capa HTTP.
 */
@Slf4j
@Controller
@RequestMapping("/administracion/donacion")
@RequiredArgsConstructor
public class DonacionController {

    private final DonacionService donacionService;
    private final WordActaDonacionService wordActa;
    private final com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService actividadService;
    private final com.usic.SistemasActivosFijosUAP.model.service.donacion.WordEtiquetasDonacionService etiquetasService;
    private final IGrupoContableService grupoService;
    private final IOrganismoFinancieroService financiadorService;
    private final IEstadoActivoService estadoActivoService;

    private static boolean puede(HttpServletRequest request) {
        return DonacionService.puede(RolesSciaf.usuarioDe(request), opciones(request));
    }

    private static Object opciones(HttpServletRequest request) {
        return request.getSession(false) == null ? null : request.getSession(false).getAttribute("opciones");
    }

    private static ResponseEntity<Map<String, Object>> sinPermiso() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                "msg", "No tiene permiso para registrar donaciones (opción «Registro de Donaciones»)."));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista(Model model, HttpServletRequest request) {
        if (!puede(request)) return "supervision/sin_permiso";

        Map<String, Object> inicial = new LinkedHashMap<>();
        inicial.put("predios", donacionService.predios());
        inicial.put("grupos", grupos());
        inicial.put("financiadores", financiadores());
        inicial.put("estados", estados());
        inicial.put("totales", donacionService.totales());
        inicial.put("maxFotos", DonacionService.MAX_FOTOS_ITEM);
        inicial.put("esAdmin", RolesSciaf.esAdministrativo(request));
        model.addAttribute("inicial", inicial);
        return "donacion/vista";
    }

    /** Corrige el acta con el formulario del alta (ver DonacionService.editar). */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/acta/{id}/editar")
    @ResponseBody
    public ResponseEntity<?> editar(@PathVariable Long id, @RequestBody DonacionRegistroRequest req, HttpServletRequest request) {
        Usuario u = RolesSciaf.usuarioDe(request);
        try {
            DonacionService.Resultado r = donacionService.editar(id, req, u, opciones(request));
            actividadService.registrar(u, com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService.MOD_ACTIVO,
                    com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService.ACC_MODIFICACION,
                    r.numero(), "Editó la donación " + r.numero() + " (" + r.cantidad() + " bien(es))", r.idDonacion());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("msg", "Cambios guardados en la donación " + r.numero() + ".");
            out.put("idDonacion", r.idDonacion());
            out.put("numero", r.numero());
            out.put("cantidad", r.cantidad());
            out.put("bienes", r.bienes());
            return ResponseEntity.ok(out);
        } catch (SecurityException e) {
            return sinPermiso();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("[DONACION] La base rechazó la edición de {}: {}", id, e.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false,
                    "msg", "No se guardó nada: algún bien que se quería borrar o rehacer tiene registros asociados en otro módulo."));
        } catch (Exception e) {
            log.error("[DONACION] Error al editar {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false,
                    "msg", "No se pudieron guardar los cambios. No se cambió nada."));
        }
    }

    /** Elimina un acta sin uso y libera su número y sus códigos D (solo administradores). */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/acta/{id}/eliminar")
    @ResponseBody
    public ResponseEntity<?> eliminar(@PathVariable Long id, @RequestBody Map<String, String> cuerpo, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        Usuario u = RolesSciaf.usuarioDe(request);
        try {
            String numero = donacionService.eliminar(id, cuerpo.get("confirmacion"), u);
            actividadService.registrar(u, com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService.MOD_ACTIVO,
                    com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService.ACC_ELIMINACION,
                    numero, "Eliminó la donación " + numero + " con sus bienes (número y códigos D liberados)", id);
            return ResponseEntity.ok(Map.of("ok", true, "msg", "La donación " + numero
                    + " se eliminó con sus bienes. Su número y sus códigos D quedaron libres."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("[DONACION] La base rechazó eliminar {}: {}", id, e.getMostSpecificCause().getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false,
                    "msg", "No se eliminó nada: algún bien tiene registros asociados en otro módulo. Use «Anular»."));
        } catch (Exception e) {
            log.error("[DONACION] Error al eliminar {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false,
                    "msg", "No se pudo eliminar. No se cambió nada."));
        }
    }

    /** Tabla de actas (DataTables, paginada en el servidor). */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/datatables")
    @ResponseBody
    public ResponseEntity<?> datatables(@RequestParam(name = "draw", defaultValue = "1") int draw,
                                        @RequestParam(name = "start", defaultValue = "0") int start,
                                        @RequestParam(name = "length", defaultValue = "25") int length,
                                        @RequestParam(name = "search[value]", required = false) String q,
                                        HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        return ResponseEntity.ok(donacionService.tabla(draw, start, length, q));
    }

    /** Fotos de un ítem del acta (multipart: item + fotos). */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/acta/{id}/fotos")
    @ResponseBody
    public ResponseEntity<?> subirFotos(@PathVariable Long id,
                                        @RequestParam(required = false) Integer item,
                                        @RequestParam(required = false) Long idActivo,
                                        @RequestParam(name = "fotos", required = false) List<org.springframework.web.multipart.MultipartFile> fotos,
                                        HttpServletRequest request) {
        try {
            return ResponseEntity.ok(Map.of("ok", true, "fotos",
                    donacionService.subirFotos(id, item, idActivo, fotos, RolesSciaf.usuarioDe(request), opciones(request))));
        } catch (SecurityException e) {
            return sinPermiso();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("[DONACION] Error subiendo fotos del acta {} ítem {}", id, item, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false,
                    "msg", "No se pudieron guardar las fotos. Intente de nuevo; si persiste, avise a sistemas."));
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/foto/{id}/quitar")
    @ResponseBody
    public ResponseEntity<?> quitarFoto(@PathVariable Long id, @RequestParam(required = false) Long idActivo,
                                        HttpServletRequest request) {
        try {
            donacionService.quitarFoto(id, idActivo, RolesSciaf.usuarioDe(request), opciones(request));
            return ResponseEntity.ok(Map.of("ok", true, "msg", "Foto quitada."));
        } catch (SecurityException e) {
            return sinPermiso();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/acta/{id}")
    @ResponseBody
    public ResponseEntity<?> acta(@PathVariable Long id, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        try {
            return ResponseEntity.ok(donacionService.detalle(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/acta/{id}/word")
    public ResponseEntity<?> actaWord(@PathVariable Long id, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        try {
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType(WordDocumentos.MIME))
                    .header("Content-Disposition", "attachment; filename=acta-donacion-" + id + ".docx")
                    .body(wordActa.generar(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Qué códigos D tomaría cada bien. Orientativo: no reserva nada. */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/previsualizar")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> previsualizar(@RequestBody Map<String, Object> cuerpo, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        Object idOficina = cuerpo.get("idOficina");
        Long id = idOficina instanceof Number n ? n.longValue() : null;
        if (id == null && idOficina instanceof String s && s.matches("\\d+")) id = Long.parseLong(s);
        Object bienes = cuerpo.get("bienes");
        List<Map<String, Object>> lista = bienes instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
        return ResponseEntity.ok(Map.of("ok", true, "bienes", donacionService.previsualizar(id, lista)));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/reservar-codigos")
    @ResponseBody
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> reservarCodigos(@RequestBody Map<String, Object> cuerpo, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        try {
            Object idOficina = cuerpo.get("idOficina");
            Long id = idOficina instanceof Number n ? n.longValue() : Long.valueOf(String.valueOf(idOficina));
            Object bienes = cuerpo.get("bienes");
            List<Map<String, Object>> lista = bienes instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
            List<ReservaCorrelativo> reservas = donacionService.reservarCodigos((String) cuerpo.get("token"), id, lista,
                    RolesSciaf.usuarioDe(request), opciones(request), ClienteReserva.de(cuerpo));
            return ResponseEntity.ok(Map.of("ok", true, "bienes", reservas.stream().map(r -> Map.of(
                    "ok", true, "desde", r.getPrefijo() + "-" + String.format("%05d", r.getDesde()) + "D",
                    "hasta", r.getPrefijo() + "-" + String.format("%05d", r.getDesde() + r.getCantidad() - 1) + "D",
                    "cantidad", r.getCantidad())).toList()));
        } catch (ClienteReserva.ReservaObsoletaException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false, "obsoleta", true, "msg", e.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (SecurityException e) { return sinPermiso(); }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/liberar-codigos")
    @ResponseBody
    public ResponseEntity<?> liberarCodigos(@RequestBody Map<String, String> cuerpo, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        try {
            donacionService.liberarCodigos(cuerpo.get("token"), RolesSciaf.usuarioDe(request), opciones(request));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (SecurityException e) { return sinPermiso(); }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/registrar")
    @ResponseBody
    public ResponseEntity<?> registrar(@RequestBody DonacionRegistroRequest req, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        Usuario u = RolesSciaf.usuarioDe(request);
        try {
            DonacionService.Resultado r = donacionService.registrar(req, u, opciones(request));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("msg", r.repetido()
                    ? "La donación " + r.numero() + " ya se había registrado en el intento anterior (la respuesta no llegó). "
                      + "Si después cambió algo en el formulario, no se aplicó: revise el acta."
                    : "Donación " + r.numero() + " registrada: " + r.cantidad() + " bien(es) con código D.");
            out.put("repetido", r.repetido());
            out.put("idDonacion", r.idDonacion());
            out.put("numero", r.numero());
            out.put("cantidad", r.cantidad());
            out.put("bienes", r.bienes());
            return ResponseEntity.ok(out);
        } catch (SecurityException e) {
            return sinPermiso();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            String causa = e.getMostSpecificCause().getMessage();
            log.error("[DONACION] La base rechazó el registro: {}", causa);
            // Solo un código o número repetido es «choque»: otro rechazo (un CHECK, un dato
            // obligatorio) no se arregla reintentando y hay que mostrarlo tal cual.
            boolean repetido = causa != null && (causa.contains("uk_activo_codigo") || causa.contains("uk_donacion_acta_numero"));
            String linea = causa == null ? "" : causa.split("\\R", 2)[0];
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false,
                    "msg", repetido
                        ? "Otro registro tomó el mismo código al mismo tiempo. No se guardó nada: vuelva a pulsar Registrar."
                        : "La base de datos rechazó el registro y no se guardó nada. Avise a sistemas con este detalle: " + linea));
        } catch (Exception e) {
            log.error("[DONACION] Error al registrar", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false,
                    "msg", "No se pudo registrar la donación. No se guardó nada. Detalle: " + e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/acta/{id}/anular")
    @ResponseBody
    public ResponseEntity<?> anular(@PathVariable Long id, @RequestBody Map<String, String> cuerpo, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        try {
            String numero = donacionService.anular(id, cuerpo.get("motivo"), RolesSciaf.usuarioDe(request));
            return ResponseEntity.ok(Map.of("ok", true, "msg", "La donación " + numero
                    + " quedó anulada. Sus bienes se cancelaron; sus códigos D no se vuelven a usar mientras el acta exista."));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("[DONACION] Error al anular {}", id, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false,
                    "msg", "No se pudo anular. No se cambió nada. Detalle: " + e.getMessage()));
        }
    }

    /** Etiquetas Word con QR de los bienes vigentes del acta. */
    @ValidarUsuarioAutenticado
    @GetMapping("/api/acta/{id}/etiquetas")
    public ResponseEntity<?> etiquetas(@PathVariable Long id, HttpServletRequest request) {
        if (!puede(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN).body("Sin permiso para Donaciones.");
        try {
            byte[] docx = etiquetasService.generar(id);
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.parseMediaType(WordDocumentos.MIME))
                    .header("Content-Disposition", "attachment; filename=etiquetas-donacion-" + id + ".docx")
                    .body(docx);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().contentType(org.springframework.http.MediaType.TEXT_PLAIN).body(e.getMessage());
        } catch (IllegalStateException e) {
            log.error("No se generaron las etiquetas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body("No se pudo generar el Word de etiquetas. Intente de nuevo.");
        }
    }

    // ── catálogos para la vista (llegan con ella: un solo pedido) ──

    private List<Map<String, Object>> grupos() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (GrupoContable g : grupoService.listarGruposContables()) {
            if (g.getCodDbf() == null || "ELIMINADO".equalsIgnoreCase(g.getEstado())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getIdGrupoContable());
            m.put("cod", String.format("%02d", g.getCodDbf()));
            // Mismo número visible que el formulario de registro (15 y 38 se muestran por su codDbf).
            Integer visible = (g.getCodContable() != null && g.getCodContable() != 15 && g.getCodContable() != 38)
                    ? g.getCodContable() : g.getCodDbf();
            m.put("visible", visible);
            m.put("nombre", g.getNombre());
            m.put("vidaUtil", g.getVidaUtil());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> financiadores() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (OrganismoFinanciero f : financiadorService.findAll()) {
            if ("ELIMINADO".equalsIgnoreCase(f.getEstado())) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", f.getIdOrganismoFinanciero());
            m.put("texto", (f.getCodOf() != null ? f.getCodOf() + " - " : "") + (f.getDescripcion() != null ? f.getDescripcion() : ""));
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> estados() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EstadoActivo e : estadoActivoService.listarEstadoActivo()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getIdEstadoActivo());
            m.put("nombre", e.getNombre());
            out.add(m);
        }
        return out;
    }
}
