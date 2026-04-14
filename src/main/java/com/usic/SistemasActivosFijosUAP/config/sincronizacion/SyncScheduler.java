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
    private long ultimoConteoTransferencias = -1;   // -1 = sin lectura previa

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
                // Continuar con la siguiente tabla aunque una falle
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

            // Solo notificar si hay cambio respecto al último ciclo
            if (conteoActual != ultimoConteoTransferencias) {
                ultimoConteoTransferencias = conteoActual;

                if (conteoActual > 0) {
                    sseRegistry.broadcast("nueva-transferencia", Map.of(
                        "pendientes", conteoActual,
                        "mensaje",   conteoActual + " transferencia(s) pendiente(s) de aprobación",
                        "timestamp", LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                    ));
                    log.info("🔔 {} transferencia(s) pendiente(s) detectada(s)", conteoActual);
                }
            }
        } catch (Exception e) {
            log.warn("No se pudo pollear transferencias: {}", e.getMessage());
    }
}
}
