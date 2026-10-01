package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.LogAccesoService;
import com.usic.SistemasActivosFijosUAP.model.dao.LogDao;
import com.usic.SistemasActivosFijosUAP.model.entity.LogAcceso;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LogAccesoServiceImpl implements LogAccesoService{

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

    private final LogDao logDao;

    @Override
    @Transactional
    public void registrarIntento(String username, String ip, String userAgent, boolean exito, String motivo) {
        registrar(username, null, null, exito, motivo, ip, userAgent, null);
    }

    @Override
    @Transactional
    public void registrar(String username, Long idUsuario, String rol, boolean exito, String motivo,
            String ip, String userAgent, String sessionId) {
        LogAcceso log = new LogAcceso();
        log.setUsername(recortar(username == null ? "" : username, 100));
        log.setUserId(idUsuario);
        log.setRol(recortar(rol, 50));
        log.setIp(recortar(ip, 45));
        log.setUserAgent(recortar(userAgent, 512));
        log.setExito(exito);
        log.setMotivo(recortar(motivo, 32));
        log.setEndpoint("/iniciar-sesion");
        log.setMetodo("POST");
        log.setSessionId(recortar(sessionId, 128));
        log.setFechaHora(LocalDateTime.now(LA_PAZ));
        logDao.save(log);
    }

    @Override
    public List<LogAcceso> ultimosDe(String username, int limite) {
        return logDao.ultimosDe(username, PageRequest.of(0, Math.max(1, limite)));
    }

    @Override
    public Map<String, LocalDateTime> ultimoIngresoPorUsuario() {
        Map<String, LocalDateTime> m = new HashMap<>();
        for (Object[] f : logDao.ultimoIngresoPorUsuario()) {
            m.put((String) f[0], (LocalDateTime) f[1]);
        }
        return m;
    }

    @Override
    public Map<String, Long> fallidosRecientesPorUsuario(int dias) {
        Map<String, Long> m = new HashMap<>();
        for (Object[] f : logDao.fallidosDesde(LocalDateTime.now(LA_PAZ).minusDays(dias))) {
            m.put((String) f[0], ((Number) f[1]).longValue());
        }
        return m;
    }

    private String recortar(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }
}
