package com.usic.SistemasActivosFijosUAP.componet;

import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.controller.auxiliar.AuxiliarController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DbfChangeHandler {
    
    private final SseEmitterRegistry sseRegistry;
    private final AuxiliarController auxiliarController;
    // Agrega los demás controllers que necesites

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /**
     * @Async: se ejecuta en un hilo separado para no bloquear el scheduler.
     * Necesitas @EnableAsync en alguna @Configuration.
     */
    @Async("syncTaskExecutor")
    @EventListener
    public void onDbfChange(DbfChangeEvent event) {
        String tabla = event.getTabla();
        log.info("🔔 Procesando cambio en tabla: {}", tabla);

        // 1. Notificar al frontend ANTES de sincronizar
        sseRegistry.broadcast("dbf-change", Map.of(
            "tabla", tabla,
            "estado", "SINCRONIZANDO",
            "mensaje", "Detectados cambios en " + nombreAmigable(tabla) + ". Sincronizando...",
            "timestamp", event.getDetectadoEn().format(FMT)
        ));

        // 2. Ejecutar sync según la tabla que cambió
        try {
            switch (tabla) {
                case "auxiliar" -> {
                    var response = auxiliarController.syncFromMounted(null, null, false);
                    notificarResultado(tabla, response, event);
                }
                // Agrega los demás casos:
                // case "predio" -> predioController.syncFromMounted(null, null, false);
                // case "entidad" -> entidadController.syncFromMounted(null, null, false);
                // etc.
                default -> log.warn("No hay handler de sync para tabla: {}", tabla);
            }
        } catch (Exception e) {
            log.error("Error en sync automático de {}: {}", tabla, e.getMessage(), e);
            sseRegistry.broadcast("dbf-change", Map.of(
                "tabla", tabla,
                "estado", "ERROR",
                "mensaje", "Error al sincronizar " + nombreAmigable(tabla) + ": " + e.getMessage(),
                "timestamp", event.getDetectadoEn().format(FMT)
            ));
        }
    }

    private void notificarResultado(String tabla, 
                                    org.springframework.http.ResponseEntity<?> response,
                                    DbfChangeEvent event) {
        boolean ok = response.getStatusCode().is2xxSuccessful();
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        String mensaje = ok
            ? String.format("✅ %s sincronizado: +%s nuevos, ~%s actualizados",
                nombreAmigable(tabla),
                body != null ? body.get("insertados") : "?",
                body != null ? body.get("actualizados") : "?")
            : "❌ Error sincronizando " + nombreAmigable(tabla);

        sseRegistry.broadcast("dbf-change", Map.of(
            "tabla", tabla,
            "estado", ok ? "COMPLETADO" : "ERROR",
            "mensaje", mensaje,
            "datos", body != null ? body : Map.of(),
            "timestamp", event.getDetectadoEn().format(FMT),
            "recargarTabla", ok  // le dice al frontend si debe recargar
        ));
    }

    private String nombreAmigable(String tabla) {
        return switch (tabla) {
            case "auxiliar" -> "Auxiliares";
            case "predio" -> "Predios";
            case "entidad" -> "Entidades";
            case "grupoContable" -> "Grupos Contables";
            default -> tabla;
        };
    }
}
