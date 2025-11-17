package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.controller.entidad.EntidadController;
import com.usic.SistemasActivosFijosUAP.controller.predio.PredioController;

import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class SyncScheduler {
    private final EntidadController entidadController;
    private final PredioController predioController;
    
    // Ejecutar cada 30 minutos
    @Scheduled(cron = "0 */30 * * * *")
    public void sincronizarAutomaticamente() {
        try {
            entidadController.syncFromMounted(null, null, false);
            predioController.syncFromMounted(null, null, false);
            // Agregar sincronización de otras tablas
        } catch (Exception e) {
            // Log error
        }
    }
}
