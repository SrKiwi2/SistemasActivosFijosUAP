package com.usic.SistemasActivosFijosUAP.model.IService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.LogAcceso;

@Service
public interface LogAccesoService {
    void registrarIntento(String username, String ip, String userAgent, boolean exito, String motivo);

    /** Intento de inicio de sesión con todo lo que se sabe del usuario y la conexión. */
    void registrar(String username, Long idUsuario, String rol, boolean exito, String motivo,
            String ip, String userAgent, String sessionId);

    /** Últimos intentos de un usuario (exitosos y fallidos), más reciente primero. */
    List<LogAcceso> ultimosDe(String username, int limite);

    /** username (en minúsculas) → fecha del último ingreso correcto. */
    Map<String, LocalDateTime> ultimoIngresoPorUsuario();

    /** username (en minúsculas) → intentos fallidos en los últimos {@code dias} días. */
    Map<String, Long> fallidosRecientesPorUsuario(int dias);
}
