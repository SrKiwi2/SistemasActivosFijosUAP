package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaLondraService;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.TransferenciaValidadaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaLondra;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/transferenciasLondra")
@RequiredArgsConstructor
public class TransferenciaLondraController {
    
    private final ITransferenciaLondraService transferenciaService;
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
        List<TransferenciaValidadaDto> lista = transferenciaService.leerYValidarPendientes();
        model.addAttribute("transferencias", lista);
        return "seguimiento/transferenciaLondra/tabla";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/aprobar/{corrT}")
    @ResponseBody
    public ResponseEntity<?> aprobar(
            HttpServletRequest request,
            @PathVariable("corrT") String corrT) {
        try {
            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            String nombreUsuario = usuario != null ? usuario.getUsuario() : "SISTEMA";

            TransferenciaLondra t = transferenciaService.aprobar(corrT, nombreUsuario);

            // Notificar a todos los clientes SSE conectados
            sseRegistry.broadcast("dbf-change", Map.of(
                "tabla",   "transferencia",
                "estado",  "COMPLETADO",
                "mensaje", "Transferencia " + corrT + " aprobada",
                "recargarTabla", true,
                "timestamp", LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
            ));

            return ResponseEntity.ok(Map.of(
                "ok",  true,
                "msg", "Transferencia " + corrT + " aprobada correctamente",
                "id",  t.getIdTransferencia()
            ));

        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("Error aprobando transferencia {}: {}", corrT, e.getMessage(), e);
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
}
