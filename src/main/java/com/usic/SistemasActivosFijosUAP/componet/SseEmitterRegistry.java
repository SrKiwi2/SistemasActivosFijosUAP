package com.usic.SistemasActivosFijosUAP.componet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SseEmitterRegistry {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Emitters globales (broadcast — igual que antes) ───────────────────────
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // ── Emitters por usuario (nuevo — para notificaciones dirigidas) ──────────
    // idUsuario → lista de emitters (puede tener varias pestañas abiertas)
    private final Map<Long, List<SseEmitter>> emittersPorUsuario =
        new ConcurrentHashMap<>();

    // ── Emitters por rol (nuevo — para notificar a todo un rol) ──────────────
    // nombreRol → Set de idUsuario conectados
    private final Map<String, Set<Long>> usuariosPorRol =
        new ConcurrentHashMap<>();

    // =========================================================================
    //  REGISTRO — igual que antes pero ahora también indexa por usuario/rol
    // =========================================================================

    /**
     * Registra un emitter anónimo (broadcast general).
     * Mantiene compatibilidad con el código existente.
     */
    public SseEmitter register(String clientId) {
        // 1. Creamos un contenedor (array de 1 posición)
        final SseEmitter[] emitterRef = new SseEmitter[1];
        
        // 2. Usamos el contenedor en las lambdas. Cuando se ejecuten, 
        // emitterRef[0] ya tendrá el valor real.
        emitterRef[0] = crearEmitter(3_600_000L, clientId,
            () -> emitters.remove(emitterRef[0]), 
            () -> emitters.remove(emitterRef[0])
        );
        
        // 3. Extraemos el emitter para usarlo normalmente
        SseEmitter emitter = emitterRef[0];
        
        emitters.add(emitter);
        enviarConectado(emitter, clientId);
        log.debug("SSE anónimo registrado: {} (total: {})", clientId, emitters.size());
        
        return emitter;
    }

    /**
     * Registra un emitter asociado a un usuario y su rol.
     * Llamar desde el endpoint SSE autenticado.
     *
     * @param idUsuario  id del usuario conectado
     * @param nombreRol  nombre del rol (ej: "PRINCIPAL")
     * @param clientId   identificador de pestaña/sesión para debug
     */
    public SseEmitter registerUsuario(Long idUsuario, String nombreRol, String clientId) {

        SseEmitter emitter = new SseEmitter(3_600_000L);

        // ── Callbacks de limpieza ────────────────────────────────────────────
        Runnable limpiar = () -> {
            eliminarEmitterDeUsuario(idUsuario, nombreRol, emitter);
            log.debug("SSE usuario={} desconectado ({})", idUsuario, clientId);
        };

        emitter.onCompletion(limpiar);
        emitter.onTimeout(() -> { emitter.complete(); limpiar.run(); });
        emitter.onError(e -> limpiar.run());

        // ── Indexar por usuario ──────────────────────────────────────────────
        emittersPorUsuario
            .computeIfAbsent(idUsuario, k -> new CopyOnWriteArrayList<>())
            .add(emitter);

        // ── Indexar por rol ──────────────────────────────────────────────────
        if (nombreRol != null && !nombreRol.isBlank()) {
            usuariosPorRol
                .computeIfAbsent(nombreRol.toUpperCase(),
                    k -> ConcurrentHashMap.newKeySet())
                .add(idUsuario);
        }

        // ── Ping inicial ─────────────────────────────────────────────────────
        enviarConectado(emitter, clientId);

        log.debug("SSE usuario={} rol={} registrado ({}). Conectados en rol: {}",
            idUsuario, nombreRol,
            clientId,
            usuariosPorRol.getOrDefault(
                nombreRol != null ? nombreRol.toUpperCase() : "",
                Set.of()).size());

        return emitter;
    }

    // =========================================================================
    //  ENVÍO — broadcast (sin cambios de interfaz) + nuevos métodos dirigidos
    // =========================================================================

    /**
     * Broadcast a TODOS los clientes conectados (anónimos + por usuario).
     * Mantiene compatibilidad total con el código existente.
     */
    public void broadcast(String eventName, Object payload) {
        String json = serializar(payload);
        if (json == null) return;

        // Anónimos
        enviarALista(emitters, eventName, json);

        // Por usuario (para que las pestañas autenticadas también reciban el broadcast)
        emittersPorUsuario.values().forEach(lista ->
            enviarALista(lista, eventName, json));
    }

    /**
     * Enviar a un usuario específico por idUsuario.
     */
    public void enviarAUsuario(Long idUsuario, String eventName, Object payload) {
        List<SseEmitter> lista = emittersPorUsuario.get(idUsuario);
        if (lista == null || lista.isEmpty()) {
            log.debug("SSE enviarAUsuario: usuario={} no conectado", idUsuario);
            return;
        }
        String json = serializar(payload);
        if (json == null) return;
        enviarALista(lista, eventName, json);
        log.debug("SSE → usuario={} evento={}", idUsuario, eventName);
    }

    /**
     * Enviar a todos los usuarios de un rol específico.
     */
    public void enviarARol(String nombreRol, String eventName, Object payload) {
        Set<Long> ids = usuariosPorRol.get(nombreRol.toUpperCase());
        if (ids == null || ids.isEmpty()) {
            log.debug("SSE enviarARol: rol='{}' sin usuarios conectados", nombreRol);
            return;
        }
        String json = serializar(payload);
        if (json == null) return;

        ids.forEach(idUsuario -> {
            List<SseEmitter> lista = emittersPorUsuario.get(idUsuario);
            if (lista != null) enviarALista(lista, eventName, json);
        });

        log.debug("SSE → rol='{}' ({} usuarios) evento={}", nombreRol, ids.size(), eventName);
    }

    /**
     * Enviar a varios roles a la vez.
     */
    public void enviarARoles(List<String> roles, String eventName, Object payload) {
        roles.forEach(rol -> enviarARol(rol, eventName, payload));
    }

    // =========================================================================
    //  INFO
    // =========================================================================

    public int countConnected() {
        return emitters.size();
    }

    public int countUsuariosConectados() {
        return emittersPorUsuario.size();
    }

    public boolean isUsuarioConectado(Long idUsuario) {
        List<SseEmitter> lista = emittersPorUsuario.get(idUsuario);
        return lista != null && !lista.isEmpty();
    }

    public Set<Long> getUsuariosConectadosEnRol(String nombreRol) {
        return usuariosPorRol.getOrDefault(
            nombreRol.toUpperCase(), Set.of());
    }

    // =========================================================================
    //  PRIVADOS
    // =========================================================================

    private void eliminarEmitterDeUsuario(Long idUsuario,
                                          String nombreRol,
                                          SseEmitter emitter) {
        List<SseEmitter> lista = emittersPorUsuario.get(idUsuario);
        if (lista != null) {
            lista.remove(emitter);
            // Si no quedan más pestañas abiertas → limpiar índice de usuario
            if (lista.isEmpty()) {
                emittersPorUsuario.remove(idUsuario);
                // Limpiar del índice de rol
                if (nombreRol != null) {
                    Set<Long> idsRol = usuariosPorRol.get(nombreRol.toUpperCase());
                    if (idsRol != null) {
                        idsRol.remove(idUsuario);
                    }
                }
            }
        }
    }

    private void enviarALista(List<SseEmitter> lista,
                               String eventName,
                               String json) {
        List<SseEmitter> muertos = new ArrayList<>();
        for (SseEmitter emitter : lista) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json));
            } catch (Exception e) {
                muertos.add(emitter);
            }
        }
        lista.removeAll(muertos);
    }

    private void enviarConectado(SseEmitter emitter, String clientId) {
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("msg", "Conexión establecida", "clientId", clientId)));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
    }

    private String serializar(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Error serializando payload SSE", e);
            return null;
        }
    }

    // Helper para el método register anónimo
    // (necesita referencia al emitter antes de crearlo — se resuelve con wrapper)
    private SseEmitter crearEmitter(long timeout, String clientId,
                                     Runnable onComplete, Runnable onTimeout) {
        SseEmitter e = new SseEmitter(timeout);
        e.onCompletion(onComplete);
        e.onTimeout(() -> { e.complete(); onTimeout.run(); });
        e.onError(err -> onComplete.run());
        return e;
    }
}
