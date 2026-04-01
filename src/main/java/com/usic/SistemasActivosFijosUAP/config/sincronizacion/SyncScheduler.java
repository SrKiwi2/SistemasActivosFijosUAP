package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.componet.DbfChangeDetectorService;

import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class SyncScheduler {
    private final DbfChangeDetectorService detectorService;

    /**
     * Sync completo de respaldo cada 6 horas,
     * independiente de si se detectaron cambios.
     * Cubre casos donde el polling pudo perder algo.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void sincronizarCompletoPeriodico() {
        // El detector ya maneja los cambios en tiempo real.
        // Aquí solo forzamos un baseline por seguridad.
        detectorService.detectarCambios();
    }
}
