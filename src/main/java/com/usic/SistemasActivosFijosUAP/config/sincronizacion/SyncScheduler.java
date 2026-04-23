package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaLondraService;
import com.usic.SistemasActivosFijosUAP.model.service.TransferenciasNotificadorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class SyncScheduler {
    private final SyncOrchestrator orchestrator;
    private final ITransferenciaLondraService transferenciaService;
    private final SseEmitterRegistry    sseRegistry;
    private final TransferenciasNotificadorService  notificadorService;

    private long ultimoConteoTransferencias = -1;

    /**
     * Sync completo de respaldo cada 6 horas.
     * Procesa todas las tablas en orden topológico,
     * empezando por las independientes.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void sincronizarCompletoPeriodico() {
        // Sync en orden de dependencias, forzar completo
        // para cubrir cambios que el polling pudo haber perdido
        for (String tabla : List.of(
                "entidad", "predio", "grupoContable", "organismoFinanciero",
                "auxiliar", "oficina", "responsable", "activo")) {
            try {
                orchestrator.sincronizarConDependencias(tabla, true);
            } catch (Exception e) {
                log.error("Error sync {}: {}", tabla, e.getMessage());
            }
        }
    }


    @Scheduled(
        initialDelayString = "${sync.poll.initial.delay.ms}",
        fixedDelayString   = "${sync.poll.interval.ms}"
    )
    public void pollearTransferenciasPendientes() {
        try {
            long conteoActual = transferenciaService.contarPendientesEnDbf();

            if (conteoActual != ultimoConteoTransferencias) {
                ultimoConteoTransferencias = conteoActual;

                if (conteoActual > 0) {
                    // ── ANTES: solo broadcast genérico ───────────────────────
                    sseRegistry.broadcast("nueva-transferencia", Map.of(
                        "pendientes", conteoActual,
                        "mensaje",   conteoActual + " transferencia(s) pendiente(s)",
                        "timestamp", LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                    ));

                    // ── NUEVO: crear notificaciones persistentes + SSE dirigido
                    try {
                        notificadorService.detectarYNotificarNuevasTransferencias();
                    } catch (Exception e) {
                        log.error("Error notificando transferencias: {}", e.getMessage(), e);
                    }

                    log.info("🔔 {} transferencia(s) pendiente(s) detectada(s)", conteoActual);
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo pollear transferencias: {}", e.getMessage());
        }
    }
}
