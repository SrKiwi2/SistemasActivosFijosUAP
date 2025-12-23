package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.controller.auxiliar.AuxiliarController;
import com.usic.SistemasActivosFijosUAP.controller.entidad.EntidadController;
import com.usic.SistemasActivosFijosUAP.controller.grupo_contable.GrupoContableController;
import com.usic.SistemasActivosFijosUAP.controller.oficina.OficinaController;
import com.usic.SistemasActivosFijosUAP.controller.organismoFinanciador.OrganismoFinanciadorController;
import com.usic.SistemasActivosFijosUAP.controller.predio.PredioController;
import com.usic.SistemasActivosFijosUAP.controller.responsable.ResponsableController;

import lombok.RequiredArgsConstructor;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class SyncScheduler {
    private final EntidadController entidadController;
    private final PredioController predioController;
    private final OficinaController oficinaController;
    private final OrganismoFinanciadorController organismoController;
    private final GrupoContableController grupoContableController;
    private final AuxiliarController auxiliarController;
    private final ResponsableController responsableController;
    
    // Ejecutar cada 30 minutos
    @Scheduled(cron = "0 */30 * * * *")
    public void sincronizarAutomaticamente() {
        try {
            // entidadController.syncFromMounted(null, null, false);
            // predioController.syncFromMounted(null, null, false);
            // oficinaController.syncFromMounted(null, null, false);
            // organismoController.syncFromMounted(null, false);
            // grupoContableController.syncFromMounted(null, false);
            // auxiliarController.syncFromMounted(null, null, false);
            // responsableController.syncFromMounted(null, false);
            // Agregar sincronización de otras tablas
        } catch (Exception e) {
            System.err.println("Error en sincronización automática: " + e.getMessage());
        }
    }
}
