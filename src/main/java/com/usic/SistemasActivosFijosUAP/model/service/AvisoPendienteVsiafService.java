package com.usic.SistemasActivosFijosUAP.model.service;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.INotificacionService;
import com.usic.SistemasActivosFijosUAP.model.dto.NotificacionSseDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisa a los administradores (ADMINISTRADOR y SUPER USUARIO) que una oficina o un
 * responsable se creó en el SCIAF y quedó <b>pendiente de subir al VSIAF</b>: quien lo creó
 * eligió no subirlo en ese momento. Lo suben después desde Oficinas / Responsables
 * («Subir al VSIAF»), o se sube solo cuando un bien normal se mueve a ellos
 * ({@code VsiafApoyoService.asegurarEnVsiaf}).
 * <p>
 * La notificación queda guardada (campanita) y, a los conectados, llega al instante por SSE.
 * Un fallo al avisar no deshace el alta: solo se registra en el log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvisoPendienteVsiafService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final INotificacionService notificacionService;
    private final SseEmitterRegistry sseRegistry;

    public void oficinaPendiente(Long idOficina, String referencia, String quien, String desde) {
        avisar("Oficina pendiente de subir al VSIAF",
                "La oficina " + referencia + " se registró en el SCIAF" + origen(desde) + " por " + quien
                + " y quedó pendiente de subir al VSIAF. Súbala desde Ámbito geográfico → Oficinas.",
                "VSIAF-PEND:OFICINA:" + idOficina, "/administracion/oficina/vista");
    }

    public void responsablePendiente(Long idResponsable, String referencia, String quien, String desde) {
        avisar("Responsable pendiente de subir al VSIAF",
                "El responsable " + referencia + " se registró en el SCIAF" + origen(desde) + " por " + quien
                + " y quedó pendiente de subir al VSIAF. Súbalo desde Usuarios → Responsables.",
                "VSIAF-PEND:RESPONSABLE:" + idResponsable, "/administracion/responsable/vista");
    }

    private static String origen(String desde) {
        return desde == null || desde.isBlank() ? "" : " (desde " + desde + ")";
    }

    private void avisar(String titulo, String mensaje, String referenciaId, String url) {
        try {
            List<Notificacion> creadas = notificacionService.crearParaRoles(
                    List.copyOf(RolesSciaf.ADMINISTRATIVOS), Notificacion.TipoNotificacion.AVISO,
                    titulo, mensaje, referenciaId, url);
            for (Notificacion n : creadas) {
                Long id = n.getUsuario().getIdUsuario();
                if (!sseRegistry.isUsuarioConectado(id)) continue;
                sseRegistry.enviarAUsuario(id, "notificacion", NotificacionSseDto.builder()
                        .idNotificacion(n.getIdNotificacion())
                        .tipo(n.getTipo().name())
                        .titulo(n.getTitulo())
                        .mensaje(n.getMensaje())
                        .referenciaId(n.getReferenciaId())
                        .urlDestino(n.getUrlDestino())
                        .fechaCreacion(n.getFechaCreacion().format(FMT))
                        .noLeidasTotal(notificacionService.contarNoLeidas(n.getUsuario()))
                        .build());
            }
            log.info("[VSIAF-PENDIENTE] Aviso «{}» a {} administrador(es)", referenciaId, creadas.size());
        } catch (Exception e) {
            log.warn("[VSIAF-PENDIENTE] No se pudo avisar {}: {}", referenciaId, e.getMessage());
        }
    }
}
