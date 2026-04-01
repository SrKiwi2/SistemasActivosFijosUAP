package com.usic.SistemasActivosFijosUAP.componet;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SseEmitterRegistry {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Registra un nuevo cliente SSE */
    public SseEmitter register(String clientId) {
        // Timeout largo: 1 hora. El cliente reconecta solo si cae.
        SseEmitter emitter = new SseEmitter(3_600_000L);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            log.debug("SSE cliente desconectado: {}", clientId);
        });
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(e -> {
            emitters.remove(emitter);
            log.debug("SSE error en cliente {}: {}", clientId, e.getMessage());
        });

        emitters.add(emitter);
        log.debug("SSE cliente registrado: {} (total: {})", clientId, emitters.size());

        // Enviar evento de bienvenida para validar la conexión
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data(Map.of("msg", "Conexión establecida", "clientId", clientId)));
        } catch (Exception e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    /** Envía un evento a TODOS los clientes conectados */
    public void broadcast(String eventName, Object payload) {
        if (emitters.isEmpty()) return;

        List<SseEmitter> muertos = new java.util.ArrayList<>();
        String json;

        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.error("Error serializando payload SSE", e);
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json));
            } catch (Exception e) {
                muertos.add(emitter);
            }
        }

        emitters.removeAll(muertos);
        log.debug("SSE broadcast '{}' → {} clientes ({} desconectados)", 
            eventName, emitters.size(), muertos.size());
    }

    public int countConnected() {
        return emitters.size();
    }
}
