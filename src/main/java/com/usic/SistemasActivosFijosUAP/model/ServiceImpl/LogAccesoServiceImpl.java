package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.LogAccesoService;
import com.usic.SistemasActivosFijosUAP.model.dao.LogDao;
import com.usic.SistemasActivosFijosUAP.model.entity.LogAcceso;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class LogAccesoServiceImpl implements LogAccesoService{

    private final LogDao logDao;

    @Override
    @Transactional
    public void registrarIntento(String username, String ip, String userAgent, boolean exito, String motivo) {
        LogAcceso log = new LogAcceso();
        log.setUsername(username);
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setExito(exito);
        log.setMotivo(motivo);
        log.setFechaHora(LocalDateTime.now(ZoneId.of("America/La_Paz")));
        logDao.save(log);
    }
}