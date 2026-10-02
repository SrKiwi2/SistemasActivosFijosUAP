package com.usic.SistemasActivosFijosUAP.model.service.seguridad;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpSession;

/**
 * Cierre de sesión por inactividad REAL del usuario.
 *
 * <p>Antes la sesión no vencía nunca mientras hubiera una pestaña abierta: el aviso de
 * presencia (cada 30 s), el autoguardado de pestañas y los refrescos automáticos son
 * peticiones, y cada petición reiniciaba el reloj de Tomcat. Si se dejaba la pestaña
 * abierta, la sesión vivía para siempre; si se cerraba, vencía a los 30 min sin aviso.
 *
 * <p>Ahora el reloj es propio ({@link #ATTR_ULTIMA}) y solo avanza con actividad de una
 * persona: abrir una pantalla (navegación) o teclado/mouse informado por el navegador
 * (sciaf-inactividad.js → {@code POST /api/sesion/estado}). Las peticiones automáticas no
 * cuentan. {@code SesionInactividadInterceptor} cierra la sesión cuando se pasa del límite,
 * y el navegador avisa {@link #getAvisoSeg()} segundos antes con la opción de seguir.
 * Las sesiones "recordadas" (casilla al ingresar) no se cierran por inactividad.
 */
@Service
public class SesionInactividadService {

    /** Instante (ms) de la última actividad de la persona en esta sesión. */
    public static final String ATTR_ULTIMA = "ultima_actividad";

    /** Margen sobre el límite propio para el vencimiento de Tomcat (pestaña cerrada). */
    private static final int MARGEN_TOMCAT_SEG = 120;

    private final long limiteMs;
    private final int avisoSeg;

    public SesionInactividadService(
            @Value("${sciaf.sesion.inactividad-min:30}") int inactividadMin,
            @Value("${sciaf.sesion.aviso-seg:120}") int avisoSeg) {
        this.limiteMs = Math.max(1, inactividadMin) * 60_000L;
        // El aviso nunca puede ser más largo que la mitad del límite.
        this.avisoSeg = (int) Math.min(Math.max(30, avisoSeg), limiteMs / 2000);
    }

    /** Al iniciar sesión. Con la pestaña cerrada, Tomcat la descarta poco después del límite. */
    public void iniciar(HttpSession session) {
        session.setAttribute(ATTR_ULTIMA, System.currentTimeMillis());
        session.setMaxInactiveInterval((int) (limiteMs / 1000) + MARGEN_TOMCAT_SEG);
    }

    /** Hubo actividad en {@code instante}. Nunca retrocede (varias pestañas informan a la vez). */
    public void marcar(HttpSession session, long instante) {
        long ahora = System.currentTimeMillis();
        long nuevo = Math.min(instante, ahora);
        Object actual = session.getAttribute(ATTR_ULTIMA);
        if (!(actual instanceof Long u) || nuevo > u) {
            session.setAttribute(ATTR_ULTIMA, nuevo);
        }
    }

    /** Milisegundos que le quedan antes de cerrarse; 0 o menos = vencida. */
    public long restanteMs(HttpSession session) {
        Object actual = session.getAttribute(ATTR_ULTIMA);
        if (!(actual instanceof Long u)) {
            // Sesión abierta antes de existir este control: se empieza a contar desde ya.
            marcar(session, System.currentTimeMillis());
            return limiteMs;
        }
        return limiteMs - (System.currentTimeMillis() - u);
    }

    public boolean vencida(HttpSession session) {
        return !recordada(session) && restanteMs(session) <= 0;
    }

    /**
     * "Mantener la sesión iniciada en este equipo": no se cierra por inactividad (vence
     * a los 5 días hábiles sin uso, ver RecordarmeService).
     */
    public boolean recordada(HttpSession session) {
        return Boolean.TRUE.equals(session.getAttribute(SesionControlService.ATTR_RECORDADA));
    }

    /** Lo que necesita el navegador para mostrar (o no) el aviso. */
    public Map<String, Object> estado(HttpSession session) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activa", true);
        m.put("recordada", recordada(session));
        // Redondeo hacia arriba: "0" solo cuando de verdad ya venció.
        m.put("restanteSeg", Math.max(0, (restanteMs(session) + 999) / 1000));
        m.put("avisoSeg", avisoSeg);
        m.put("limiteSeg", limiteMs / 1000);
        return m;
    }

    public long getLimiteMs() {
        return limiteMs;
    }

    public int getAvisoSeg() {
        return avisoSeg;
    }
}
