package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaLondraService;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaAccionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaCabeceraDao;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.AccionTransferenciaRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.transferencia.TransferenciaAgrupadaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaAccion;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaCabecera;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/transferenciasLondra")
@RequiredArgsConstructor
public class TransferenciaLondraController {
    
    private final ITransferenciaLondraService transferenciaService;
    private final ITransferenciaCabeceraDao cabeceraRepo;
    private final ITransferenciaAccionDao accionRepo;
    private final SseEmitterRegistry    sseRegistry;
    private static final Logger log = LoggerFactory.getLogger(TransferenciaLondraController.class);

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista() {
        return "seguimiento/transferenciaLondra/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model,
            @RequestParam(name = "q", required = false) String q) {
        try {
            List<TransferenciaAgrupadaDto> lista =
                transferenciaService.leerYValidarAgrupado();
            model.addAttribute("transferencias", lista);
            model.addAttribute("errorCarga", null);
        } catch (Exception e) {
            log.error("Error cargando transferencias: {}", e.getMessage(), e);
            model.addAttribute("transferencias", List.of());
            model.addAttribute("errorCarga", e.getMessage());
        }
        return "seguimiento/transferenciaLondra/tabla";
}

    @PostMapping("/aprobar")
    @ResponseBody
    public ResponseEntity<?> aprobar(
            HttpServletRequest request,
            @RequestBody Map<String, String> body) {
        try {
            String corrT = body.get("corrT");
            if (corrT == null || corrT.isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "corrT es requerido"));
            }

            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            String nombreUsuario = usuario != null ? usuario.getUsuario() : "SISTEMA";

            // ← Ahora retorna TransferenciaCabecera en lugar de TransferenciaLondra
            TransferenciaCabecera t = transferenciaService.aprobar(corrT, nombreUsuario);

            sseRegistry.broadcast("dbf-change", Map.of(
                "tabla",         "transferencia",
                "estado",        "COMPLETADO",
                "mensaje",       "Transferencia " + corrT + " aprobada ("
                                + t.getDetalles().size() + " activos)",
                "recargarTabla", true,
                "timestamp",     LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            ));

            return ResponseEntity.ok(Map.of(
                "ok",      true,
                "msg",     "Transferencia aprobada correctamente ("
                        + t.getDetalles().size() + " activo(s))",
                "id",      t.getIdCabecera(),
                "activos", t.getDetalles().size()
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("Error aprobando {}: {}", body.get("corrT"), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    /** Badge de notificación — llamado por polling del frontend */
    @GetMapping("/conteo-pendientes")
    @ResponseBody
    public ResponseEntity<?> conteoPendientes() {
        return ResponseEntity.ok(Map.of(
            "pendientes", transferenciaService.contarPendientesEnDbf()
        ));
    }

    // ── RECHAZAR ──────────────────────────────────────────────────────────────
    @PostMapping("/rechazar")
    @ResponseBody
    public ResponseEntity<?> rechazar(
            HttpServletRequest request,
            @RequestBody AccionTransferenciaRequest body) {
        try {
            if (body.getCorrT() == null || body.getCorrT().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "corrT es requerido"));
            }
            if (body.getMotivo() == null || body.getMotivo().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "El motivo es obligatorio"));
            }

            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            String nombreUsuario = usuario != null ? usuario.getUsuario() : "SISTEMA";

            TransferenciaCabecera t = transferenciaService.rechazar(
                body.getCorrT(), body.getMotivo(), nombreUsuario);

            // SSE broadcast para actualizar tabla en todos los clientes
            sseRegistry.broadcast("dbf-change", Map.of(
                "tabla",         "transferencia",
                "estado",        "RECHAZADO",
                "mensaje",       "Transferencia " + body.getCorrT() + " rechazada",
                "recargarTabla", true,
                "timestamp",     LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            ));

            return ResponseEntity.ok(Map.of(
                "ok",  true,
                "msg", "Transferencia rechazada correctamente",
                "id",  t.getIdCabecera()
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("Error rechazando {}: {}", body.getCorrT(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    // ── OBSERVAR ──────────────────────────────────────────────────────────────
    @PostMapping("/observar")
    @ResponseBody
    public ResponseEntity<?> observar(
            HttpServletRequest request,
            @RequestBody AccionTransferenciaRequest body) {
        try {
            if (body.getCorrT() == null || body.getCorrT().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "corrT es requerido"));
            }
            if (body.getMotivo() == null || body.getMotivo().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "El motivo es obligatorio"));
            }

            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            String nombreUsuario = usuario != null ? usuario.getUsuario() : "SISTEMA";

            TransferenciaCabecera t = transferenciaService.observar(
                body.getCorrT(), body.getMotivo(), nombreUsuario);

            sseRegistry.broadcast("dbf-change", Map.of(
                "tabla",         "transferencia",
                "estado",        "OBSERVADO",
                "mensaje",       "Transferencia " + body.getCorrT() + " observada",
                "recargarTabla", true,
                "timestamp",     LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            ));

            return ResponseEntity.ok(Map.of(
                "ok",  true,
                "msg", "Transferencia observada correctamente",
                "id",  t.getIdCabecera()
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("Error observando {}: {}", body.getCorrT(), e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    // ── HISTORIAL DE ACCIONES de una transferencia ────────────────────────────
    @GetMapping("/historial-acciones/{corrT}")
    @ResponseBody
    public ResponseEntity<?> historialAcciones(@PathVariable String corrT) {
        try {
            TransferenciaCabecera cabecera = cabeceraRepo.findByCorrT(corrT)
                .orElseThrow(() -> new IllegalArgumentException(
                    "Transferencia no encontrada: " + corrT));

            List<TransferenciaAccion> acciones =
                accionRepo.findByCabeceraOrderByFechaAccionAsc(cabecera);

            DateTimeFormatter fmt =
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

            List<Map<String, Object>> resultado = acciones.stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",            a.getIdAccion());
                    m.put("tipoAccion",    a.getTipoAccion().name());
                    m.put("motivo",        a.getMotivo());
                    m.put("usuarioAccion", a.getUsuarioAccion());
                    m.put("fechaAccion",   a.getFechaAccion() != null
                        ? a.getFechaAccion().format(fmt) : null);
                    m.put("estadoCallback", a.getEstadoCallback() != null
                        ? a.getEstadoCallback().name() : null);
                    return m;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                "corrT",    corrT,
                "estado",   cabecera.getEstado().name(),
                "acciones", resultado
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }
}
