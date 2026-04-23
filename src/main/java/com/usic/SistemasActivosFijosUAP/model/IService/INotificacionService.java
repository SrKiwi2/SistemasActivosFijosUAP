package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

public interface INotificacionService {
    // Crear una notificación para un usuario específico
    Notificacion crear(Usuario usuario,
                       Notificacion.TipoNotificacion tipo,
                       String titulo,
                       String mensaje,
                       String referenciaId,
                       String urlDestino);

    // Crear para todos los usuarios de un rol
    List<Notificacion> crearParaRol(String nombreRol,
                                    Notificacion.TipoNotificacion tipo,
                                    String titulo,
                                    String mensaje,
                                    String referenciaId,
                                    String urlDestino);

    // Crear para varios roles a la vez
    List<Notificacion> crearParaRoles(List<String> nombreRoles,
                                      Notificacion.TipoNotificacion tipo,
                                      String titulo,
                                      String mensaje,
                                      String referenciaId,
                                      String urlDestino);

    // Obtener no leídas de un usuario
    List<Notificacion> obtenerNoLeidas(Usuario usuario);

    // Obtener historial paginado
    Page<Notificacion> obtenerHistorial(Usuario usuario, Pageable pageable);

    // Conteo no leídas
    long contarNoLeidas(Usuario usuario);

    // Marcar una como leída
    void marcarLeida(Long idNotificacion, Usuario usuario);

    // Marcar todas como leídas
    int marcarTodasLeidas(Usuario usuario);
}
