package com.usic.SistemasActivosFijosUAP.model.IService;

import org.springframework.stereotype.Service;

@Service
public interface LogAccesoService {
    void registrarIntento(String username, String ip, String userAgent, boolean exito, String motivo);
}
