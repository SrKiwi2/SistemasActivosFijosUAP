package com.usic.SistemasActivosFijosUAP.componet;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.config.sincronizacion.SyncOrchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DbfChangeHandler {
    
    private final SyncOrchestrator orchestrator;

    @Async("syncTaskExecutor")
    @EventListener
    public void onDbfChange(DbfChangeEvent event) {
        log.info("🔔 Cambio detectado: {} — iniciando sync ordenado", event.getTabla());

        // El orquestador se encarga de:
        // 1. Resolver el orden de dependencias
        // 2. Emitir notificaciones SSE
        // 3. Ejecutar los syncs en secuencia
        orchestrator.sincronizarConDependencias(event.getTabla(), false);
    }
}
