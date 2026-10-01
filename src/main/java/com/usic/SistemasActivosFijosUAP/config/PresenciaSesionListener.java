package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.model.service.supervision.PresenciaService;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Cuando una sesión termina (cerrar sesión, cierre forzado por el administrador o
 * vencimiento), deja de figurar en "Usuarios conectados" en ese momento, sin esperar a
 * que venza su último aviso. Spring Boot registra solo este listener por ser un bean.
 */
@Component
public class PresenciaSesionListener implements HttpSessionListener {

    private final PresenciaService presencia;

    public PresenciaSesionListener(PresenciaService presencia) {
        this.presencia = presencia;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        presencia.quitar(se.getSession().getId());
    }
}
