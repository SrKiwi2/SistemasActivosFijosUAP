package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/eventos")
@RequiredArgsConstructor
public class SseController {
    
    private final SseEmitterRegistry sseRegistry;

    /**
     * El navegador se conecta aquí para recibir notificaciones en tiempo real.
     * URL: /api/eventos/stream
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        String clientId = UUID.randomUUID().toString().substring(0, 8);
        return sseRegistry.register(clientId);
    }

    @GetMapping("/clientes-conectados")
    public int clientesConectados() {
        return sseRegistry.countConnected();
    }
}
