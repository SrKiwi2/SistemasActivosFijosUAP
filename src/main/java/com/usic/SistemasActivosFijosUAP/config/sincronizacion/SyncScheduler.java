package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import java.util.List;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.componet.DbfChangeDetectorService;

import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class SyncScheduler {
    private final SyncOrchestrator orchestrator;

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
}
