package com.usic.SistemasActivosFijosUAP.model.service.seguridad;

import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.IConsultaApiActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ConsultaApiActivo;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Registra cada llamada a la API pública de ficha de activo en {@code consulta_api_activo}.
 *
 * <p>Corre en su propia transacción: la consulta del activo es de solo lectura y, si
 * guardar la bitácora falla, la respuesta al sistema cliente no debe romperse.
 */
@Slf4j
@Service
public class ConsultaApiActivoRegistroService {

    private static final ZoneId LA_PAZ = ZoneId.of("America/La_Paz");

    private final IConsultaApiActivoDao dao;

    public ConsultaApiActivoRegistroService(IConsultaApiActivoDao dao) {
        this.dao = dao;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(HttpServletRequest request, String codigo, String resultado) {
        try {
            ConsultaApiActivo c = new ConsultaApiActivo();
            c.setFechaHora(LocalDateTime.now(LA_PAZ));
            c.setCodigoConsultado(recortar(codigo, 60));
            c.setResultado(resultado);
            c.setSistema(recortar(request.getHeader("X-Sistema"), 100));
            String xff = request.getHeader("X-Forwarded-For");
            c.setForwardedFor(recortar(xff, 255));
            c.setIp(recortar(xff != null && !xff.isBlank() ? xff.split(",")[0].trim() : request.getRemoteAddr(), 45));
            c.setUserAgent(recortar(request.getHeader("User-Agent"), 512));
            c.setOrigen(recortar(request.getHeader("Origin"), 255));
            c.setReferer(recortar(request.getHeader("Referer"), 512));
            dao.save(c);
        } catch (Exception e) {
            log.warn("[API-FICHA] No se pudo registrar la consulta de '{}': {}", codigo, e.getMessage());
        }
    }

    private static String recortar(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
