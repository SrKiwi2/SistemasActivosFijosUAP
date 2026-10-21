package com.usic.SistemasActivosFijosUAP.controller.personas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.SistemasActivosFijosUAP.componet.VsiafDisponibilidad;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.entity.SolicitudAutorizacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.personas.CargosService;
import com.usic.SistemasActivosFijosUAP.model.service.personas.HistorialLotesService;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanSeparacion;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PersonasCargosService;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanCargos;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.AutorizacionService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Personas y cargos: ficha de una persona con todas sus oficinas, cambio de cargo en
 * varias a la vez y unificación de la misma persona registrada varias veces.
 * <p>
 * {@code /administracion/**} es {@code permitAll()}: el permiso se revisa aquí, en cada
 * endpoint. Entra ADMINISTRADOR / SUPER USUARIO o quien tenga la opción del menú; solo los
 * dos primeros aplican directo, el resto deja una solicitud de autorización.
 */
@Controller
@RequestMapping("/administracion/personas-cargos")
@RequiredArgsConstructor
public class PersonasCargosController {

    private static final Logger log = LoggerFactory.getLogger(PersonasCargosController.class);
    public static final String OPCION = "opcion_personas_cargos";

    private final PersonasCargosService service;
    private final CargosService cargosService;
    private final HistorialLotesService historialLotes;
    private final AutorizacionService autorizacionService;
    private final VsiafDisponibilidad vsiaf;
    private final ObjectMapper mapper = new ObjectMapper();

    /** ¿Puede entrar al módulo? */
    public static boolean puedeUsar(HttpServletRequest request) {
        if (RolesSciaf.esAdministrativo(request)) return true;
        Object opciones = request.getSession(false) != null ? request.getSession().getAttribute("opciones") : null;
        return opciones instanceof Set<?> set && set.contains(OPCION);
    }

    @GetMapping("/vista")
    public String vista(HttpServletRequest request, Model model,
                        @RequestParam(required = false) Long persona,
                        @RequestParam(required = false) String cargo) {
        if (!puedeUsar(request)) return "personasCargos/sin_permiso";
        model.addAttribute("aplicaDirecto", RolesSciaf.esAdministrativo(request));
        // Sin VSIAF a la vista (localhost con la base de producción, montaje caído): se puede
        // mirar y ver la vista previa, pero no aplicar ni pedir autorización.
        boolean conVsiaf = vsiaf.dbf("Personas y cargos");
        model.addAttribute("vsiafDisponible", conVsiaf);
        model.addAttribute("motivoSinVsiaf", conVsiaf ? null : vsiaf.motivoDbf());
        Map<String, Object> inicial = new LinkedHashMap<>();
        inicial.put("persona", persona);
        inicial.put("cargo", cargo);
        model.addAttribute("inicial", inicial);
        return "personasCargos/vista";
    }

    @GetMapping("/buscar")
    @ResponseBody
    public ResponseEntity<?> buscar(HttpServletRequest request, @RequestParam(required = false) String q,
                                    @RequestParam(required = false) List<Long> excluir) {
        if (!puedeUsar(request)) return prohibido();
        return ResponseEntity.ok(service.buscar(q, excluir));
    }

    @GetMapping("/ficha/{id}")
    @ResponseBody
    public ResponseEntity<?> ficha(HttpServletRequest request, @PathVariable Long id,
                                   @RequestParam(required = false) List<Long> con) {
        if (!puedeUsar(request)) return prohibido();
        try {
            Map<String, Object> f = service.ficha(id, con);
            f.put("aplicaDirecto", RolesSciaf.esAdministrativo(request));
            return ResponseEntity.ok(f);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    /** Vista previa: no guarda nada. */
    @PostMapping("/plan")
    @ResponseBody
    public ResponseEntity<?> plan(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        try {
            PlanPersona.Resultado r = service.plan(PersonasCargosService.decisionDe(cuerpo));
            Map<String, Object> m = PersonasCargosService.aMapa(r);
            m.put("ok", true);
            m.put("resumen", service.resumen(r));
            m.put("aplicaDirecto", RolesSciaf.esAdministrativo(request));
            return ResponseEntity.ok(m);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    /**
     * Aplica (administradores) o deja la solicitud (el resto). Exige la firma de la vista
     * previa: si algo cambió entretanto, no se aplica.
     */
    @PostMapping("/aplicar")
    @ResponseBody
    public ResponseEntity<?> aplicar(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        Usuario usuario = RolesSciaf.usuarioDe(request);
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("ok", false, "msg", "La sesión expiró."));
        if (!vsiaf.dbf("Personas y cargos")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg",
                    "Aquí solo se puede mirar: " + vsiaf.motivoDbf() + " Aplique el cambio desde el servidor del SCIAF."));
        }
        String firma = cuerpo.get("firma") == null ? null : cuerpo.get("firma").toString();
        String motivo = cuerpo.get("motivo") == null ? null : cuerpo.get("motivo").toString().trim();
        try {
            PlanPersona.Decision d = PersonasCargosService.decisionDe(cuerpo);

            if (!RolesSciaf.esAdministrativo(request)) {
                PlanPersona.Resultado r = service.plan(d);
                if (firma == null || !firma.equals(r.firma())) {
                    throw new IllegalStateException("Los datos cambiaron desde la vista previa. Vuelva a revisarla.");
                }
                if (!r.errores().isEmpty()) throw new IllegalStateException(String.join(" ", r.errores()));
                if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Indique el motivo de la solicitud.");
                exigirSinSolicitudPendiente(PersonasCargosService.personasDe(service.datosSolicitud(d, firma, motivo)));
                autorizacionService.solicitar(PersonasCargosService.TIPO_SOLICITUD, PersonasCargosService.MODULO_SOLICITUD,
                        d.idPrincipal(), r.nuevaPersona().nombreCompleto(), service.resumen(r),
                        service.datosSolicitud(d, firma, motivo), motivo, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                        "msg", "Solicitud enviada. Se aplicará cuando el revisor la apruebe; le avisaremos aquí mismo."));
            }

            PersonasCargosService.Aplicado a = service.aplicarDirecto(d, firma, motivo, usuario);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", true);
            m.put("vsiafOk", a.ok());
            m.put("msg", a.msg());
            m.put("lote", a.lote());
            return ResponseEntity.ok(m);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("[PERSONAS] No se pudo aplicar el cambio", e);
            return ResponseEntity.internalServerError().body(Map.of("ok", false,
                    "msg", "No se pudo guardar: " + e.getMessage() + ". No se aplicó nada."));
        }
    }

    // ── Pestaña «Cargos»: juntar cargos escritos de distintas formas (fase 3) ──

    @GetMapping("/cargos/grupos")
    @ResponseBody
    public ResponseEntity<?> gruposCargos(HttpServletRequest request) {
        if (!puedeUsar(request)) return prohibido();
        return ResponseEntity.ok(cargosService.grupos());
    }

    @GetMapping("/cargos/buscar")
    @ResponseBody
    public ResponseEntity<?> buscarCargos(HttpServletRequest request, @RequestParam(required = false) String q) {
        if (!puedeUsar(request)) return prohibido();
        return ResponseEntity.ok(cargosService.buscar(q));
    }

    @PostMapping("/cargos/plan")
    @ResponseBody
    public ResponseEntity<?> planCargos(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        try {
            PlanCargos.Resultado r = cargosService.plan(CargosService.decisionDe(cuerpo));
            Map<String, Object> m = CargosService.aMapa(r);
            m.put("ok", true);
            m.put("resumen", cargosService.resumen(r));
            m.put("aplicaDirecto", RolesSciaf.esAdministrativo(request));
            return ResponseEntity.ok(m);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    @PostMapping("/cargos/aplicar")
    @ResponseBody
    public ResponseEntity<?> aplicarCargos(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        if (!vsiaf.dbf("Personas y cargos")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg",
                    "Aquí solo se puede mirar: " + vsiaf.motivoDbf() + " Aplique el cambio desde el servidor del SCIAF."));
        }
        Usuario usuario = RolesSciaf.usuarioDe(request);
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("ok", false, "msg", "La sesión expiró."));
        String firma = cuerpo.get("firma") == null ? null : cuerpo.get("firma").toString();
        String motivo = cuerpo.get("motivo") == null ? null : cuerpo.get("motivo").toString().trim();
        try {
            PlanCargos.Decision d = CargosService.decisionDe(cuerpo);
            if (!RolesSciaf.esAdministrativo(request)) {
                PlanCargos.Resultado r = cargosService.plan(d);
                if (firma == null || !firma.equals(r.firma())) {
                    throw new IllegalStateException("Los datos cambiaron desde la vista previa. Vuelva a revisarla.");
                }
                if (!r.errores().isEmpty()) throw new IllegalStateException(String.join(" ", r.errores()));
                if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Indique el motivo de la solicitud.");
                Map<String, Object> datos = cargosService.datosSolicitud(d, firma, motivo);
                exigirSinSolicitudPendiente(CargosService.MODULO_SOLICITUD, CargosService.cargosDe(datos), true);
                autorizacionService.solicitar(CargosService.TIPO_SOLICITUD, CargosService.MODULO_SOLICITUD,
                        d.idElegido(), r.texto(), cargosService.resumen(r), datos, motivo, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                        "msg", "Solicitud enviada. Se aplicará cuando el revisor la apruebe; le avisaremos aquí mismo."));
            }
            CargosService.Aplicado a = cargosService.aplicarDirecto(d, firma, motivo, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "vsiafOk", a.ok(), "msg", a.msg(), "lote", a.lote()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("[CARGOS] No se pudo aplicar el cambio", e);
            return ResponseEntity.internalServerError().body(Map.of("ok", false,
                    "msg", "No se pudo guardar: " + e.getMessage() + ". No se aplicó nada."));
        }
    }

    /** «No son el mismo cargo»: solo deja de sugerirlos juntos; no cambia ningún dato del VSIAF. */
    @PostMapping("/cargos/descartar")
    @ResponseBody
    public ResponseEntity<?> descartarCargos(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        try {
            int n = cargosService.descartar(PersonasCargosService.idsDe(cuerpo.get("ids")), RolesSciaf.usuarioDe(request));
            return ResponseEntity.ok(Map.of("ok", true, "msg", n > 0 ? "Listo: no se volverán a sugerir juntos." : "Ya estaban marcados."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    // ── Oficina repetida: faltantes del registro que sobra (fase 9) ──

    @GetMapping("/repetidos/bienes/{idResponsable}")
    @ResponseBody
    public ResponseEntity<?> bienesDeRepetido(HttpServletRequest request, @PathVariable Long idResponsable) {
        if (!puedeUsar(request)) return prohibido();
        return ResponseEntity.ok(service.bienesDe(idResponsable));
    }

    @PostMapping("/repetidos/faltantes")
    @ResponseBody
    public ResponseEntity<?> faltantesDeRepetido(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        try {
            var r = service.faltantesDeRepetido(PersonasCargosService.aLong(cuerpo.get("idPrincipal")),
                    PersonasCargosService.aLong(cuerpo.get("idResponsable")), PersonasCargosService.idsDe(cuerpo.get("ids")),
                    cuerpo.get("documento") == null ? null : cuerpo.get("documento").toString(),
                    cuerpo.get("observacion") == null ? null : cuerpo.get("observacion").toString(),
                    RolesSciaf.usuarioDe(request));
            return ResponseEntity.ok(Map.of("ok", true, "msg", r.mensaje(), "numero", r.numero(),
                    "pdf", "/administracion/control-activos/custodia/actas/" + r.idActa() + "/pdf"));
        } catch (IllegalArgumentException | IllegalStateException
                 | com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    @PostMapping("/repetidos/juntar")
    @ResponseBody
    public ResponseEntity<?> juntarCustodias(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        try {
            var r = service.juntarCustodias(PersonasCargosService.aLong(cuerpo.get("idQueda")),
                    PersonasCargosService.aLong(cuerpo.get("idSobra")), RolesSciaf.usuarioDe(request));
            return ResponseEntity.ok(Map.of("ok", true, "completo", r.completo(), "msg", r.mensaje()));
        } catch (IllegalArgumentException | IllegalStateException
                 | com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    // ── Por arreglar (fase 8): lista de trabajo ──

    @GetMapping("/arreglar")
    @ResponseBody
    public ResponseEntity<?> porArreglar(HttpServletRequest request) {
        if (!puedeUsar(request)) return prohibido();
        return ResponseEntity.ok(service.porArreglar());
    }

    /** Baja de personas sin oficinas: solo en el SCIAF (el VSIAF no tiene personas), solo administradores. */
    @PostMapping("/arreglar/baja")
    @ResponseBody
    public ResponseEntity<?> darDeBaja(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        try {
            int n = service.darDeBajaSinOficinas(PersonasCargosService.idsDe(cuerpo.get("ids")), RolesSciaf.usuarioDe(request));
            return ResponseEntity.ok(Map.of("ok", true, "msg", n > 0
                    ? n + " persona(s) dada(s) de baja."
                    : "Ninguna: ya no estaban libres (alguien les asignó algo)."));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    // ── Pasar oficinas a otra persona (fase 6) ──

    @PostMapping("/separar/plan")
    @ResponseBody
    public ResponseEntity<?> planSeparar(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        try {
            PlanSeparacion.Resultado r = service.planSeparacion(PersonasCargosService.decisionSeparacionDe(cuerpo));
            Map<String, Object> m = PersonasCargosService.aMapa(r);
            m.put("ok", true);
            m.put("resumen", service.resumenSeparacion(r));
            m.put("aplicaDirecto", RolesSciaf.esAdministrativo(request));
            return ResponseEntity.ok(m);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    @PostMapping("/separar/aplicar")
    @ResponseBody
    public ResponseEntity<?> separar(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        if (!vsiaf.dbf("Personas y cargos")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg",
                    "Aquí solo se puede mirar: " + vsiaf.motivoDbf() + " Aplique el cambio desde el servidor del SCIAF."));
        }
        Usuario usuario = RolesSciaf.usuarioDe(request);
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("ok", false, "msg", "La sesión expiró."));
        String firma = cuerpo.get("firma") == null ? null : cuerpo.get("firma").toString();
        String motivo = cuerpo.get("motivo") == null ? null : cuerpo.get("motivo").toString().trim();
        try {
            PlanSeparacion.Decision d = PersonasCargosService.decisionSeparacionDe(cuerpo);
            if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Indique el motivo.");
            if (!RolesSciaf.esAdministrativo(request)) {
                PlanSeparacion.Resultado r = service.planSeparacion(d);
                if (firma == null || !firma.equals(r.firma())) throw new IllegalStateException("Los datos cambiaron desde la vista previa. Vuelva a revisarla.");
                if (!r.errores().isEmpty()) throw new IllegalStateException(String.join(" ", r.errores()));
                Map<String, Object> datos = service.datosSeparacion(d, firma, motivo);
                exigirSinSolicitudPendiente(PersonasCargosService.personasDe(datos));
                autorizacionService.solicitar(PersonasCargosService.TIPO_SEPARAR, PersonasCargosService.MODULO_SOLICITUD,
                        d.idOrigen(), r.destino() == null ? "?" : r.destino().nombreCompleto(), service.resumenSeparacion(r),
                        datos, motivo, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                        "msg", "Solicitud enviada. Se aplicará cuando el revisor la apruebe; le avisaremos aquí mismo."));
            }
            PersonasCargosService.Aplicado a = service.separarDirecto(d, firma, motivo, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "vsiafOk", a.ok(), "msg", a.msg(), "lote", a.lote()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("[PERSONAS] No se pudo pasar las oficinas", e);
            return ResponseEntity.internalServerError().body(Map.of("ok", false,
                    "msg", "No se pudo guardar: " + e.getMessage() + ". No se aplicó nada."));
        }
    }

    // ── Pestaña «Historial»: lotes y reversión (fase 5) ──

    @GetMapping("/historial/lotes")
    @ResponseBody
    public ResponseEntity<?> lotes(HttpServletRequest request, @RequestParam(required = false) String q,
                                   @RequestParam(required = false) String usuario,
                                   @RequestParam(required = false) String desde, @RequestParam(required = false) String hasta) {
        if (!puedeUsar(request)) return prohibido();
        try {
            return ResponseEntity.ok(historialLotes.lotes(q, usuario, fecha(desde), fecha(hasta)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    @GetMapping("/historial/lote")
    @ResponseBody
    public ResponseEntity<?> lote(HttpServletRequest request, @RequestParam String lote) {
        if (!puedeUsar(request)) return prohibido();
        return ResponseEntity.ok(historialLotes.detalle(lote));
    }

    @PostMapping("/historial/revertir/plan")
    @ResponseBody
    public ResponseEntity<?> planReversion(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        String lote = cuerpo.get("lote") == null ? null : cuerpo.get("lote").toString();
        if (lote == null || lote.isBlank()) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Falta el lote."));
        try {
            PlanReversion.Resultado r = historialLotes.plan(lote);
            Map<String, Object> m = HistorialLotesService.aMapa(r);
            m.put("ok", true);
            m.put("resumen", historialLotes.resumen(r));
            m.put("aplicaDirecto", RolesSciaf.esAdministrativo(request));
            return ResponseEntity.ok(m);
        } catch (RuntimeException e) {
            log.warn("[HISTORIAL] No se pudo armar la vista previa del lote {}: {}", lote, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No se pudo armar la vista previa: " + e.getMessage()));
        }
    }

    @PostMapping("/historial/revertir/aplicar")
    @ResponseBody
    public ResponseEntity<?> revertir(HttpServletRequest request, @RequestBody Map<String, Object> cuerpo) {
        if (!puedeUsar(request)) return prohibido();
        if (!vsiaf.dbf("Personas y cargos")) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg",
                    "Aquí solo se puede mirar: " + vsiaf.motivoDbf() + " Revierta desde el servidor del SCIAF."));
        }
        Usuario usuario = RolesSciaf.usuarioDe(request);
        if (usuario == null) return ResponseEntity.status(401).body(Map.of("ok", false, "msg", "La sesión expiró."));
        String lote = cuerpo.get("lote") == null ? null : cuerpo.get("lote").toString();
        String firma = cuerpo.get("firma") == null ? null : cuerpo.get("firma").toString();
        String motivo = cuerpo.get("motivo") == null ? null : cuerpo.get("motivo").toString().trim();
        try {
            if (lote == null || lote.isBlank()) throw new IllegalArgumentException("Falta el lote.");
            if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Indique el motivo de la reversión.");
            if (!RolesSciaf.esAdministrativo(request)) {
                PlanReversion.Resultado r = historialLotes.plan(lote);
                if (firma == null || !firma.equals(r.firma())) throw new IllegalStateException("Los datos cambiaron desde la vista previa. Vuelva a revisarla.");
                if (!r.errores().isEmpty()) throw new IllegalStateException(String.join(" ", r.errores()));
                Long id = historialLotes.idDeLote(lote);
                autorizacionService.solicitar(HistorialLotesService.TIPO_SOLICITUD, HistorialLotesService.MODULO_SOLICITUD,
                        id, "Lote " + lote, "Revertir: " + historialLotes.resumen(r),
                        Map.of("lote", lote, "firma", firma, "motivo", motivo), motivo, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                        "msg", "Solicitud enviada. Se revertirá cuando el revisor la apruebe; le avisaremos aquí mismo."));
            }
            HistorialLotesService.Aplicado a = historialLotes.revertirDirecto(lote, firma, motivo, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "vsiafOk", a.ok(), "msg", a.msg(), "lote", a.lote()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("[HISTORIAL] No se pudo revertir el lote {}", lote, e);
            return ResponseEntity.internalServerError().body(Map.of("ok", false,
                    "msg", "No se pudo revertir: " + e.getMessage() + ". No se aplicó nada."));
        }
    }

    private static java.time.LocalDate fecha(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(s.trim());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Fecha inválida: " + s);
        }
    }

    /** Una sola solicitud pendiente a la vez sobre cada persona. */
    private void exigirSinSolicitudPendiente(Set<Long> personas) {
        exigirSinSolicitudPendiente(PersonasCargosService.MODULO_SOLICITUD, personas, false);
    }

    /** Una sola solicitud pendiente a la vez sobre cada persona (o cada cargo). */
    private void exigirSinSolicitudPendiente(String modulo, Set<Long> ids, boolean sonCargos) {
        List<Long> enCurso = new ArrayList<>();
        for (SolicitudAutorizacion s : autorizacionService.pendientes()) {
            if (!modulo.equals(s.getModulo())) continue;
            try {
                Map<String, Object> datos = mapper.readValue(s.getDatosJson(), new TypeReference<Map<String, Object>>() {});
                Set<Long> suyos = sonCargos ? CargosService.cargosDe(datos) : PersonasCargosService.personasDe(datos);
                for (Long id : suyos) if (ids.contains(id)) enCurso.add(id);
            } catch (Exception e) {
                log.warn("[PERSONAS] Solicitud {} con datos ilegibles: {}", s.getIdSolicitud(), e.getMessage());
            }
        }
        if (!enCurso.isEmpty()) {
            throw new IllegalStateException("Ya hay una solicitud pendiente sobre " + (sonCargos ? "alguno de estos cargos" : "esta(s) persona(s)")
                    + ". Espere a que el revisor la resuelva.");
        }
    }

    private static ResponseEntity<?> prohibido() {
        return ResponseEntity.status(403).body(Map.of("ok", false, "msg", "No tiene permiso para Personas y cargos."));
    }
}
