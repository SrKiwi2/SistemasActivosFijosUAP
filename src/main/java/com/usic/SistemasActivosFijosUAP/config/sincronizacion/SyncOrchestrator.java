package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.controller.auxiliar.AuxiliarController;
import com.usic.SistemasActivosFijosUAP.controller.oficina.OficinaController;
import com.usic.SistemasActivosFijosUAP.controller.responsable.ResponsableController;
import com.usic.SistemasActivosFijosUAP.model.service.ActivoSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncOrchestrator {
    private final SseEmitterRegistry  sseRegistry;
    private final AuxiliarController  auxiliarController;
    private final OficinaController   oficinaController;
    private final ResponsableController responsableController;
    private final ActivoSyncService   activoSyncService;
    // Agrega los demás controllers según necesites

    // ── Grafo de dependencias (tabla → sus dependencias en orden) ────────────
    private static final Map<String, List<String>> DEPENDENCIAS = Map.of(
        "activo",            List.of("oficina", "responsable", "grupoContable", "auxiliar"),
        "responsable",       List.of("oficina"),
        "auxiliar",          List.of("grupoContable"),
        "oficina",           List.of(),
        "grupoContable",     List.of(),
        "entidad",           List.of(),
        "predio",            List.of("entidad"),
        "organismoFinanciero", List.of()
    );

    // ── Estado en memoria ────────────────────────────────────────────────────

    /** Tablas que están siendo sincronizadas ahora mismo (anti-cascade) */
    private final Set<String> enProceso = ConcurrentHashMap.newKeySet();

    /** Última vez que se sincronizó con éxito cada tabla */
    private final Map<String, Instant> ultimaSync = new ConcurrentHashMap<>();

    /** Si una dependencia se sincronizó en los últimos N minutos, se omite */
    private static final Duration UMBRAL_DEPENDENCIA = Duration.ofMinutes(3);

    // ── API pública ──────────────────────────────────────────────────────────

    /**
     * Sincroniza una tabla respetando el orden de dependencias.
     * Las dependencias solo se re-sincronizan si han pasado más de UMBRAL_DEPENDENCIA.
     */
    public void sincronizarConDependencias(String tabla, boolean forzarCompleto) {
        List<String> ordenEjecucion = resolverOrden(tabla);
        log.info("🔀 Orden de sync para '{}': {}", tabla, ordenEjecucion);

        for (String t : ordenEjecucion) {
            boolean estaTablaObjetivo = t.equals(tabla);

            // Anti-cascade: no re-entrar si ya está en proceso
            if (enProceso.contains(t)) {
                log.info("⏭ '{}' ya está en proceso, omitiendo.", t);
                continue;
            }

            // Para dependencias: omitir si fue sincronizada recientemente
            if (!estaTablaObjetivo && fueRecientementeSincronizada(t)) {
                log.info("⏭ Dependencia '{}' sincronizada hace menos de {}min, omitiendo.",
                    t, UMBRAL_DEPENDENCIA.toMinutes());
                continue;
            }

            ejecutarSync(t, estaTablaObjetivo ? forzarCompleto : false);
        }
    }

    // ── Privados ─────────────────────────────────────────────────────────────

    private void ejecutarSync(String tabla, boolean forzarCompleto) {
        if (!enProceso.add(tabla)) return; // ya está, doble check

        try {
            String hora = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));

            sseRegistry.broadcast("dbf-change", Map.of(
                "tabla",   tabla,
                "estado",  "SINCRONIZANDO",
                "mensaje", "Sincronizando " + nombreAmigable(tabla) + "...",
                "timestamp", hora
            ));

            ResponseEntity<?> response = despacharSync(tabla, forzarCompleto);

            if (response != null) {
                ultimaSync.put(tabla, Instant.now());
                boolean ok = response.getStatusCode().is2xxSuccessful();

                @SuppressWarnings("unchecked")
                Map<String, Object> body = (Map<String, Object>) response.getBody();

                sseRegistry.broadcast("dbf-change", Map.of(
                    "tabla",        tabla,
                    "estado",       ok ? "COMPLETADO" : "ERROR",
                    "mensaje",      construirMensaje(tabla, body, ok),
                    "datos",        body != null ? body : Map.of(),
                    "recargarTabla", ok,
                    "timestamp",    hora
                ));
            }

        } catch (Exception e) {
            log.error("Error sync '{}': {}", tabla, e.getMessage(), e);
            sseRegistry.broadcast("dbf-change", Map.of(
                "tabla",   tabla,
                "estado",  "ERROR",
                "mensaje", "Error en " + nombreAmigable(tabla) + ": " + e.getMessage()
            ));
        } finally {
            enProceso.remove(tabla);
        }
    }

    private ResponseEntity<?> despacharSync(String tabla, boolean forzarCompleto) {
        return switch (tabla) {
            case "auxiliar"    -> auxiliarController.syncFromMounted(null, null, forzarCompleto);
            case "oficina"     -> oficinaController.syncFromMounted(null, null, forzarCompleto);
            case "responsable" -> responsableController.syncFromMounted(null, forzarCompleto);
            case "activo"      -> activoSyncService.syncFromMounted(null, forzarCompleto);
            // Agrega: entidad, predio, grupoContable, organismoFinanciero
            default -> { log.warn("Sin handler de sync para: {}", tabla); yield null; }
        };
    }

    /**
     * Resolución topológica de dependencias.
     * Ejemplo para "activo": [grupoContable, oficina, responsable, auxiliar, activo]
     */
    private List<String> resolverOrden(String tabla) {
        List<String> orden   = new ArrayList<>();
        Set<String> visitado = new LinkedHashSet<>();
        resolverRec(tabla, visitado, orden);
        return orden;
    }

    private void resolverRec(String tabla, Set<String> visitado, List<String> orden) {
        if (visitado.contains(tabla)) return;
        visitado.add(tabla);
        for (String dep : DEPENDENCIAS.getOrDefault(tabla, List.of())) {
            resolverRec(dep, visitado, orden);
        }
        orden.add(tabla); // se agrega DESPUÉS de sus dependencias
    }

    private boolean fueRecientementeSincronizada(String tabla) {
        Instant ultima = ultimaSync.get(tabla);
        return ultima != null &&
               Duration.between(ultima, Instant.now()).compareTo(UMBRAL_DEPENDENCIA) < 0;
    }

    private String construirMensaje(String tabla, Map<String, Object> body, boolean ok) {
        if (!ok) return "❌ Error sincronizando " + nombreAmigable(tabla);
        if (body == null) return "✅ " + nombreAmigable(tabla) + " sincronizado";
        return String.format("✅ %s: +%s nuevos, ~%s actualizados",
            nombreAmigable(tabla),
            body.getOrDefault("insertados", "?"),
            body.getOrDefault("actualizados", "?"));
    }

    private String nombreAmigable(String tabla) {
        return switch (tabla) {
            case "activo"            -> "Activos";
            case "oficina"           -> "Oficinas";
            case "responsable"       -> "Responsables";
            case "auxiliar"          -> "Auxiliares";
            case "grupoContable"     -> "Grupos Contables";
            case "predio"            -> "Predios";
            case "entidad"           -> "Entidades";
            case "organismoFinanciero" -> "Organismos Financiadores";
            default -> tabla;
        };
    }
}
