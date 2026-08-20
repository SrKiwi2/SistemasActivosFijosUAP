package com.usic.SistemasActivosFijosUAP.config;

import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;

import groovy.util.logging.Log4j2;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Log4j2
public class UsuarioAutenticadoInterceptor implements HandlerInterceptor{
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String uri = request.getRequestURI();
        if (uri.startsWith("/api/eventos")) {
            return true; // SSE pasa siempre
        }

        if (handler instanceof HandlerMethod handlerMethod) {
            ValidarUsuarioAutenticado anotacion = handlerMethod.getMethodAnnotation(ValidarUsuarioAutenticado.class);
            if (anotacion != null) {

                // getSession(false): NO crear sesión acá.
                //
                // Con getSession() se creaba una sesión vacía solo para comprobar que
                // no tenía "persona" y redirigir — un desperdicio, y sobre todo un
                // error cuando este preHandle corre en un despacho a /error (una
                // petición que ya falló): ahí la respuesta puede estar comprometida y
                // crear la sesión lanza IllegalStateException, que tapa el error real
                // con "Cannot create a session after the response has been committed".
                HttpSession session = request.getSession(false);

                if (session == null || session.getAttribute("persona") == null) {
                    // Si la respuesta ya salió no hay nada que redirigir: solo se corta.
                    if (!response.isCommitted()) {
                        response.sendRedirect("/form-login");
                    }
                    return false;
                }
            }
        }
        return true;
    }
}
