package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.model.service.adicional.AdicionalService;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import lombok.extern.slf4j.Slf4j;

/**
 * Cuando una sesión termina (cerrar sesión, nuevo ingreso en el mismo navegador, cierre
 * forzado o vencimiento), libera los correlativos de adicionales que tenía reservados.
 * La pestaña ya no puede hacerlo: sin sesión, su pedido de liberación se rechaza.
 */
@Slf4j
@Component
public class ReservaCodigoSesionListener implements HttpSessionListener {

    private final AdicionalService adicionalService;

    public ReservaCodigoSesionListener(AdicionalService adicionalService) {
        this.adicionalService = adicionalService;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        try {
            adicionalService.liberarCodigosDeSesion(se.getSession().getId());
        } catch (Exception e) {
            log.warn("[ADICIONAL] No se liberaron las reservas de la sesión que terminó: {}", e.getMessage());
        }
    }
}
