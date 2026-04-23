package com.usic.SistemasActivosFijosUAP.model.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.INotificacionService;
import com.usic.SistemasActivosFijosUAP.model.dao.INotificacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaCabeceraDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.NotificacionSseDto;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SolTransferenciaDbf;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferenciasNotificadorService {
     private final INotificacionService    notificacionService;
    private final SseEmitterRegistry      sseRegistry;
    private final ITransferenciaCabeceraDao cabeceraRepo;
    private final IUsuarioDao             usuarioDao;
    private final INotificacionDao        notificacionDao;

    @Value("${legacy.dbf.transferencias.path}")
    private String transferenciasPath;

    private final JavaDbfService dbfService;

    // Roles que reciben notificaciones de transferencia
    private static final List<String> ROLES_NOTIFICADOS =
        List.of("PRINCIPAL", "ADMINISTRADOR");

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // =========================================================================
    //  Llamado desde SyncScheduler — detecta corrT nuevos
    // =========================================================================

    /**
     * Compara los corrT pendientes del DBF contra los ya aprobados en BD.
     * Por cada corrT nuevo (no aprobado, no notificado antes) crea
     * notificaciones persistentes y las envía por SSE a los roles.
     *
     * Diseño anti-duplicado: usa existsByUsuarioAndReferenciaIdAndTipo
     * para no crear la misma notificación dos veces.
     */
    public void detectarYNotificarNuevasTransferencias() {
        List<SolTransferenciaDbf> pendientes;
        try {
            pendientes = dbfService
                .listarSolTransferenciasAll(Path.of(transferenciasPath), null)
                .stream()
                .filter(f -> esPendiente(f.getEstadoT()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("⚠️ No se pudo leer sol_transferencias.dbf: {}", e.getMessage());
            return;
        }

        if (pendientes.isEmpty()) return;

        // Agrupar por corrT — cada grupo es una transferencia
        Map<String, List<SolTransferenciaDbf>> porCorrT = pendientes.stream()
            .filter(f -> f.getCorrT() != null && !f.getCorrT().isBlank())
            .collect(Collectors.groupingBy(
                f -> f.getCorrT().trim(),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        for (Map.Entry<String, List<SolTransferenciaDbf>> entry : porCorrT.entrySet()) {
            String corrT = entry.getKey();
            List<SolTransferenciaDbf> activos = entry.getValue();

            try {
                procesarCorrT(corrT, activos);
            } catch (Exception e) {
                log.error("Error procesando notificación para corrT={}: {}",
                    corrT, e.getMessage(), e);
            }
        }
    }

    // =========================================================================
    //  PRIVADOS
    // =========================================================================

    private void procesarCorrT(String corrT, List<SolTransferenciaDbf> activos) {

        // No notificar si ya fue aprobada
        if (cabeceraRepo.existsByCorrT(corrT)) return;

        SolTransferenciaDbf primero = activos.get(0);

        boolean esExterna = primero.getUnidadO() != null
            && primero.getUnidadD() != null
            && !primero.getUnidadO().trim().equalsIgnoreCase(
                primero.getUnidadD().trim());

        String titulo = "Nueva transferencia pendiente";
        String mensaje = String.format(
            "Correlativo: %s | %s → %s | %d activo(s) | Receptor: %s",
            corrT,
            primero.getUnidadO() != null ? primero.getUnidadO().trim() : "?",
            primero.getUnidadD() != null ? primero.getUnidadD().trim() : "?",
            activos.size(),
            primero.getNomRecep() != null ? primero.getNomRecep().trim() : "?"
        );
        String urlDestino = "/administracion/transferenciasLondra";

        // Crear notificaciones para cada usuario de los roles correspondientes
        List<Notificacion> creadas = notificacionService.crearParaRoles(
            ROLES_NOTIFICADOS,
            Notificacion.TipoNotificacion.TRANSFERENCIA_NUEVA,
            titulo,
            mensaje,
            corrT,       // referenciaId = corrT
            urlDestino
        );

        if (creadas.isEmpty()) return; // ya existían todas o sin usuarios

        log.info("🔔 Notificaciones creadas: corrT={} tipo={} destinatarios={}",
            corrT, esExterna ? "EXTERNA" : "INTERNA", creadas.size());

        // Enviar SSE individualizado a cada usuario notificado
        creadas.forEach(notif -> enviarSseAUsuario(notif));

        // También emitir evento de recarga de tabla a todos
        // (para que la tabla de transferencias se actualice si está abierta)
        sseRegistry.enviarARoles(ROLES_NOTIFICADOS, "nueva-transferencia", Map.of(
            "corrT",     corrT,
            "pendientes", porCorrT_size(pendientes_count()),
            "mensaje",   activos.size() + " activo(s) pendiente(s) — " + corrT,
            "timestamp", LocalDateTime.now().format(FMT),
            "recargarTabla", true
        ));
    }

    private void enviarSseAUsuario(Notificacion notif) {
        Long idUsuario = notif.getUsuario().getIdUsuario();

        if (!sseRegistry.isUsuarioConectado(idUsuario)) {
            // Usuario no conectado ahora mismo — la notificación ya está en BD
            // la verá cuando se conecte
            log.debug("  Usuario id={} no conectado — notificación guardada en BD",
                idUsuario);
            return;
        }

        long noLeidas = notificacionService.contarNoLeidas(notif.getUsuario());

        NotificacionSseDto dto = NotificacionSseDto.builder()
            .idNotificacion(notif.getIdNotificacion())
            .tipo(notif.getTipo().name())
            .titulo(notif.getTitulo())
            .mensaje(notif.getMensaje())
            .referenciaId(notif.getReferenciaId())
            .urlDestino(notif.getUrlDestino())
            .fechaCreacion(notif.getFechaCreacion().format(FMT))
            .noLeidasTotal(noLeidas)
            .build();

        sseRegistry.enviarAUsuario(idUsuario, "notificacion", dto);
        log.debug("  SSE → usuario={} noLeidas={}", idUsuario, noLeidas);
    }

    private boolean esPendiente(String estadoT) {
        if (estadoT == null) return false;
        return Set.of("ENVIADO", "PENDIENTE", "PEND", "P", "0")
            .contains(estadoT.trim().toUpperCase());
    }

    // helpers para el map interno de procesarCorrT
    private long pendientes_count() {
        try {
            return dbfService
                .listarSolTransferenciasAll(Path.of(transferenciasPath), null)
                .stream().filter(f -> esPendiente(f.getEstadoT())).count();
        } catch (Exception e) { return 0L; }
    }
    private long porCorrT_size(long count) { return count; }
}
