package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.INotificacionService;
import com.usic.SistemasActivosFijosUAP.model.dao.INotificacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion.TipoNotificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificacionServiceImpl implements INotificacionService {
    
    private final INotificacionDao  notificacionDao;
    private final IUsuarioDao       usuarioDao;  // necesito ver tu DAO, asumo que existe

    @Override
    public Notificacion crear(
            Usuario usuario,
            Notificacion.TipoNotificacion tipo,
            String titulo, String mensaje,
            String referenciaId, String urlDestino) {

        Notificacion n = new Notificacion();
        n.setUsuario(usuario);
        n.setTipo(tipo);
        n.setTitulo(titulo);
        n.setMensaje(mensaje);
        n.setReferenciaId(referenciaId);
        n.setUrlDestino(urlDestino);
        n.setLeida(false);
        n.setFechaCreacion(LocalDateTime.now());
        n.setEstado("ACTIVO");

        Notificacion guardada = notificacionDao.save(n);
        log.debug("📬 Notificación creada → usuario={} tipo={} ref={}",
            usuario.getUsuario(), tipo, referenciaId);
        return guardada;
    }

    @Override
    public List<Notificacion> crearParaRol(
            String nombreRol, Notificacion.TipoNotificacion tipo,
            String titulo, String mensaje,
            String referenciaId, String urlDestino) {

        List<Usuario> usuarios = usuarioDao
            .findByRolNombreAndEstado(nombreRol, "ACTIVO");

        if (usuarios.isEmpty()) {
            log.warn("⚠️ crearParaRol: no hay usuarios activos con rol='{}'", nombreRol);
            return List.of();
        }

        return usuarios.stream()
            .filter(u -> !notificacionDao.existsByUsuarioAndReferenciaIdAndTipo(
                u, referenciaId, tipo))  // ← anti-duplicado
            .map(u -> crear(u, tipo, titulo, mensaje, referenciaId, urlDestino))
            .collect(Collectors.toList());
    }

    @Override
    public List<Notificacion> crearParaRoles(
            List<String> nombreRoles, Notificacion.TipoNotificacion tipo,
            String titulo, String mensaje,
            String referenciaId, String urlDestino) {

        return nombreRoles.stream()
            .flatMap(rol -> crearParaRol(
                rol, tipo, titulo, mensaje, referenciaId, urlDestino).stream())
            .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notificacion> obtenerNoLeidas(Usuario usuario) {
        return notificacionDao
            .findByUsuarioAndLeidaFalseOrderByFechaCreacionDesc(usuario);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Notificacion> obtenerHistorial(Usuario usuario, Pageable pageable) {
        return notificacionDao
            .findByUsuarioOrderByFechaCreacionDesc(usuario, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long contarNoLeidas(Usuario usuario) {
        return notificacionDao.countByUsuarioAndLeidaFalse(usuario);
    }

    @Override
    public void marcarLeida(Long idNotificacion, Usuario usuario) {
        notificacionDao.findById(idNotificacion).ifPresent(n -> {
            if (!n.getUsuario().getIdUsuario().equals(usuario.getIdUsuario())) {
                throw new IllegalStateException("No autorizado");
            }
            n.setLeida(true);
            n.setFechaLectura(LocalDateTime.now());
            notificacionDao.save(n);
        });
    }

    @Override
    public int marcarTodasLeidas(Usuario usuario) {
        return notificacionDao.marcarTodasLeidas(usuario, LocalDateTime.now());
    }

    // Limpieza automática cada día a las 3am — notificaciones leídas > 30 días
    @Scheduled(cron = "0 0 3 * * *")
    public void limpiarNotificacionesAntiguas() {
        LocalDateTime hace30dias = LocalDateTime.now().minusDays(30);
        int eliminadas = notificacionDao.eliminarLeidasAnterioresA(hace30dias);
        if (eliminadas > 0) {
            log.info("🧹 Notificaciones antiguas eliminadas: {}", eliminadas);
        }
    }
}
