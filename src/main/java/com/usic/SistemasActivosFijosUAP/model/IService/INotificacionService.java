package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.usic.SistemasActivosFijosUAP.model.dto.ComunicadoDetalleDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Comunicado;
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

    // Marcar una como leída (confirmación explícita de lectura)
    void marcarLeida(Long idNotificacion, Usuario usuario);

    // Marcar todas como leídas
    int marcarTodasLeidas(Usuario usuario);

    // Marcar como entregadas (recibidas por el cliente) las notificaciones del
    // usuario cuyos ids se indican; devuelve cuántas se actualizaron.
    int marcarEntregadas(List<Long> ids, Usuario usuario);

    // ── Comunicados (módulo administrador/responsable) ────────────────────────

    /**
     * Crea y envía un comunicado: resuelve la audiencia (usuarios ACTIVOS del
     * momento), persiste el {@link Comunicado}, genera una {@link Notificacion}
     * por destinatario enlazada al comunicado y empuja SSE a los conectados.
     *
     * @param idsUsuarios usado solo cuando alcance = USUARIOS
     * @param idOficina   usado solo cuando alcance = OFICINA
     */
    Comunicado enviarComunicado(Usuario emisor,
                                Notificacion.TipoNotificacion tipo,
                                String titulo,
                                String mensaje,
                                String urlDestino,
                                boolean importante,
                                Comunicado.Alcance alcance,
                                Set<Long> idsUsuarios,
                                Long idOficina);

    // Listado paginado de comunicados enviados (panel de control)
    Page<Comunicado> listarComunicados(Pageable pageable);

    // Conteos rápidos de un comunicado: [destinatarios, recibieron, leyeron]
    long[] resumenComunicado(Comunicado comunicado);

    // Conteos agregados por lote: idComunicado → [entregadas, leidas]
    Map<Long, long[]> resumenComunicadoLote(List<Long> idsComunicado);

    // Detalle de acuses por destinatario de un comunicado
    ComunicadoDetalleDto detalleComunicado(Long idComunicado);
}
