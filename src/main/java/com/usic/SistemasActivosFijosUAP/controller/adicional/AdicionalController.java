package com.usic.SistemasActivosFijosUAP.controller.adicional;

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
import org.springframework.web.multipart.MultipartFile;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.dto.adicional.AdicionalRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.adicional.AdicionalService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Administración de activos → Registro de Adicionales: bienes que no estaban en el sistema,
 * con datos opcionales, su código antiguo y fotos. Solo en el SCIAF (ver {@link AdicionalService}).
 * Responde JSON {@code { ok, msg, … }}, como la plantilla de pantallas.
 */
@Slf4j
@Controller
@RequestMapping("/administracion/adicional")
@RequiredArgsConstructor
public class AdicionalController {

    private final AdicionalService adicionalService;
    private final ActividadService actividadService;
    private final IGrupoContableService grupoService;
    private final IOrganismoFinancieroService financiadorService;
    private final IEstadoActivoService estadoActivoService;

    private static Object opciones(HttpServletRequest request) {
        return request.getSession(false) == null ? null : request.getSession(false).getAttribute("opciones");
    }

    private static boolean puede(HttpServletRequest request) {
        return AdicionalService.puede(RolesSciaf.usuarioDe(request), opciones(request));
    }

    private static ResponseEntity<Map<String, Object>> sinPermiso() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                "msg", "No tiene permiso para Activos adicionales (opción «Registro de Adicionales»)."));
    }

    private static ResponseEntity<Map<String, Object>> error(Exception e, String que) {
        if (e instanceof SecurityException) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "msg", e.getMessage()));
        if (e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
        if (e instanceof org.springframework.dao.DataIntegrityViolationException dive) {
            String causa = dive.getMostSpecificCause().getMessage();
            log.error("[ADICIONAL] La base rechazó {}: {}", que, causa);
            boolean repetido = causa != null && causa.contains("uk_activo_codigo");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("ok", false, "msg", repetido
                    ? "Otro registro tomó el mismo código al mismo tiempo. No se guardó nada: vuelva a intentarlo."
                    : "La base de datos rechazó la operación y no se guardó nada. Detalle: " + (causa == null ? "" : causa.split("\\R", 2)[0])));
        }
        log.error("[ADICIONAL] Error en {}", que, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("ok", false, "msg", "No se pudo completar. No se guardó nada."));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista(Model model, HttpServletRequest request) {
        if (!puede(request)) return "supervision/sin_permiso";
        Map<String, Object> inicial = new LinkedHashMap<>();
        inicial.put("predios", adicionalService.predios());
        inicial.put("grupos", grupos());
        inicial.put("financiadores", financiadores());
        inicial.put("estados", estados());
        inicial.put("totales", adicionalService.totales());
        inicial.put("esAdmin", RolesSciaf.esAdministrativo(request));
        inicial.put("maxFotos", AdicionalService.MAX_FOTOS);
        model.addAttribute("inicial", inicial);
        return "adicional/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/datatables")
    @ResponseBody
    public ResponseEntity<?> datatables(@RequestParam(name = "draw", defaultValue = "1") int draw,
                                        @RequestParam(name = "start", defaultValue = "0") int start,
                                        @RequestParam(name = "length", defaultValue = "25") int length,
                                        @RequestParam(name = "search[value]", required = false) String q,
                                        HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        return ResponseEntity.ok(adicionalService.tabla(draw, start, length, q, RolesSciaf.usuarioDe(request), opciones(request)));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/activo/{id}")
    @ResponseBody
    public ResponseEntity<?> detalle(@PathVariable Long id, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        try {
            return ResponseEntity.ok(adicionalService.detalle(id, RolesSciaf.usuarioDe(request), opciones(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/previsualizar")
    @ResponseBody
    public ResponseEntity<?> previsualizar(@RequestParam(required = false) Long idPredio, @RequestParam(required = false) Long idOficina,
                                           @RequestParam(required = false) Long idGrupo, @RequestParam(defaultValue = "1") int cantidad,
                                           HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        return ResponseEntity.ok(adicionalService.previsualizar(idPredio, idOficina, idGrupo, cantidad, RolesSciaf.usuarioDe(request), opciones(request)));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/registrar")
    @ResponseBody
    public ResponseEntity<?> registrar(@RequestBody AdicionalRequest req, HttpServletRequest request) {
        Usuario u = RolesSciaf.usuarioDe(request);
        try {
            List<Map<String, Object>> creados = adicionalService.registrar(req, u, opciones(request));
            actividadService.registrar(u, ActividadService.MOD_ACTIVO, ActividadService.ACC_REGISTRO, "ADICIONALES",
                    "Registró " + creados.size() + " activo(s) adicional(es)", creados.size(), null);
            long conCodigo = creados.stream().filter(c -> c.get("codigo") != null).count();
            return ResponseEntity.ok(Map.of("ok", true, "activos", creados,
                    "msg", creados.size() + " activo(s) adicional(es) registrado(s)"
                         + (conCodigo < creados.size() ? "; " + (creados.size() - conCodigo) + " sin código nuevo (falta predio o grupo contable)." : ".")));
        } catch (Exception e) {
            return error(e, "el registro");
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/activo/{id}/editar")
    @ResponseBody
    public ResponseEntity<?> editar(@PathVariable Long id, @RequestBody AdicionalRequest req, HttpServletRequest request) {
        Usuario u = RolesSciaf.usuarioDe(request);
        try {
            Map<String, Object> r = adicionalService.editar(id, req, u, opciones(request));
            Object codigo = r.get("codigo"), anterior = r.get("codigoAnterior");
            actividadService.registrar(u, ActividadService.MOD_ACTIVO, ActividadService.ACC_MODIFICACION,
                    codigo != null ? String.valueOf(codigo) : "ADICIONAL " + id, "Corrigió el activo adicional " + (codigo != null ? codigo : id), id);
            String msg = "Cambios guardados."
                    + (codigo != null && !codigo.equals(anterior) ? " Código asignado: " + codigo + (anterior != null ? " (antes " + anterior + ")." : ".") : "");
            Map<String, Object> out = new LinkedHashMap<>(r);
            out.put("ok", true);
            out.put("msg", msg);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            return error(e, "la corrección");
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/activo/{id}/eliminar")
    @ResponseBody
    public ResponseEntity<?> eliminar(@PathVariable Long id, HttpServletRequest request) {
        if (!puede(request)) return sinPermiso();
        Usuario u = RolesSciaf.usuarioDe(request);
        try {
            String ref = adicionalService.eliminar(id, u);
            actividadService.registrar(u, ActividadService.MOD_ACTIVO, ActividadService.ACC_ELIMINACION, ref,
                    "Eliminó el activo adicional " + ref, id);
            return ResponseEntity.ok(Map.of("ok", true, "msg", "Activo adicional " + ref + " eliminado."));
        } catch (Exception e) {
            return error(e, "la eliminación");
        }
    }

    /** Fotos a uno o varios activos (multipart: idsActivo + fotos). */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/fotos")
    @ResponseBody
    public ResponseEntity<?> subirFotos(@RequestParam(name = "idsActivo") List<Long> idsActivo,
                                        @RequestParam(name = "fotos", required = false) List<MultipartFile> fotos,
                                        HttpServletRequest request) {
        try {
            return ResponseEntity.ok(Map.of("ok", true, "fotos",
                    adicionalService.subirFotos(idsActivo, fotos, RolesSciaf.usuarioDe(request), opciones(request))));
        } catch (Exception e) {
            return error(e, "las fotos");
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/foto/{id}/quitar")
    @ResponseBody
    public ResponseEntity<?> quitarFoto(@PathVariable Long id, HttpServletRequest request) {
        try {
            adicionalService.quitarFoto(id, RolesSciaf.usuarioDe(request), opciones(request));
            return ResponseEntity.ok(Map.of("ok", true, "msg", "Foto quitada."));
        } catch (Exception e) {
            return error(e, "quitar la foto");
        }
    }

    /** Etiquetas con QR de los activos marcados (PDF, se abre en otra pestaña). */
    @ValidarUsuarioAutenticado
    @GetMapping("/api/etiquetas")
    public ResponseEntity<?> etiquetas(@RequestParam(name = "ids") List<Long> ids, HttpServletRequest request) {
        if (!puede(request)) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(org.springframework.http.MediaType.TEXT_PLAIN).body("Sin permiso para Activos adicionales.");
        try {
            byte[] pdf = adicionalService.etiquetas(ids, RolesSciaf.usuarioDe(request), opciones(request));
            return ResponseEntity.ok()
                    .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "inline; filename=etiquetas-adicionales.pdf")
                    .body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().contentType(org.springframework.http.MediaType.TEXT_PLAIN).body(e.getMessage());
        } catch (IllegalStateException e) {
            log.error("No se generaron las etiquetas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(org.springframework.http.MediaType.TEXT_PLAIN)
                    .body("No se pudo generar el PDF de etiquetas. Intente de nuevo.");
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
            out.add(Map.of("id", f.getIdOrganismoFinanciero(),
                    "texto", (f.getCodOf() != null ? f.getCodOf() + " - " : "") + (f.getDescripcion() != null ? f.getDescripcion() : "")));
        }
        return out;
    }

    private List<Map<String, Object>> estados() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EstadoActivo e : estadoActivoService.listarEstadoActivo()) out.add(Map.of("id", e.getIdEstadoActivo(), "nombre", e.getNombre()));
        return out;
    }
}
