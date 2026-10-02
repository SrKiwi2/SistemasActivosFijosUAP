package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.stereotype.Component;

import com.usic.SistemasActivosFijosUAP.model.service.seguridad.SesionControlService;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

/**
 * Cuando Tomcat destruye una sesión (cerrar sesión, inactividad, vencimiento), la marca
 * cerrada en "Mis sesiones abiertas". Las recordadas siguen abiertas en su equipo: la
 * cookie las vuelve a armar. Spring Boot registra el listener por ser un bean.
 */
@Component
public class SesionControlListener implements HttpSessionListener {

    private final SesionControlService control;

    public SesionControlListener(SesionControlService control) {
        this.control = control;
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        control.alDestruirse(se.getSession());
    }
}
