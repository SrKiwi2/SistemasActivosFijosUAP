package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.IService.INotificacionService;
import com.usic.SistemasActivosFijosUAP.model.dao.IComunicadoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.INotificacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.ComunicadoDetalleDto;
import com.usic.SistemasActivosFijosUAP.model.dto.NotificacionSseDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Comunicado;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion.TipoNotificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class NotificacionServiceImpl implements INotificacionService {
    
    private final INotificacionDao  notificacionDao;
    private final IUsuarioDao       usuarioDao;
    private final IComunicadoDao    comunicadoDao;
    private final IOficinaDao       oficinaDao;
    private final SseEmitterRegistry sseRegistry;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

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

    @Override
    public int marcarEntregadas(List<Long> ids, Usuario usuario) {
        if (ids == null || ids.isEmpty()) return 0;
        return notificacionDao.marcarEntregadas(usuario, ids, LocalDateTime.now());
    }

    // =========================================================================
    //  COMUNICADOS
    // =========================================================================

    @Override
    public Comunicado enviarComunicado(
            Usuario emisor, Notificacion.TipoNotificacion tipo,
            String titulo, String mensaje, String urlDestino, boolean importante,
            Comunicado.Alcance alcance, Set<Long> idsUsuarios, Long idOficina) {

        // 1) Resolver audiencia sobre usuarios ACTIVOS del momento.
        //    (Un usuario creado después no recibe comunicados anteriores.)
        List<Usuario> destinatarios;
        String alcanceDetalle;

        switch (alcance) {
            case TODOS -> {
                destinatarios = usuarioDao.listarUsuarios();
                alcanceDetalle = "Todos los usuarios";
            }
            case OFICINA -> {
                if (idOficina == null)
                    throw new IllegalArgumentException("Debe indicar la oficina.");
                destinatarios = usuarioDao.findActivosPorOficina(idOficina);
                Oficina of = oficinaDao.findById(idOficina).orElse(null);
                alcanceDetalle = "Oficina: "
                    + (of != null ? of.getNombre() : ("#" + idOficina));
            }
            case USUARIOS -> {
                destinatarios = (idsUsuarios == null || idsUsuarios.isEmpty())
                    ? List.of()
                    : usuarioDao.findAllByIdUsuarioIn(idsUsuarios);
                alcanceDetalle = destinatarios.size() + " usuario(s) específico(s)";
            }
            default -> throw new IllegalArgumentException("Alcance no soportado.");
        }

        if (destinatarios.isEmpty()) {
            throw new IllegalStateException(
                "No se encontraron destinatarios activos para el envío.");
        }

        // 2) Persistir el comunicado (el "envío").
        Comunicado com = new Comunicado();
        com.setEmisor(emisor);
        com.setTipo(tipo);
        com.setTitulo(titulo);
        com.setMensaje(mensaje);
        com.setUrlDestino(urlDestino);
        com.setImportante(importante);
        com.setAlcance(alcance);
        com.setAlcanceDetalle(alcanceDetalle);
        com.setFechaEnvio(LocalDateTime.now());
        com.setTotalDestinatarios(destinatarios.size());
        com.setEstado("ACTIVO");
        final Comunicado guardado = comunicadoDao.save(com);

        // 3) Una notificación por destinatario, enlazada al comunicado.
        List<Notificacion> creadas = destinatarios.stream().map(u -> {
            Notificacion n = new Notificacion();
            n.setUsuario(u);
            n.setComunicado(guardado);
            n.setTipo(tipo);
            n.setTitulo(titulo);
            n.setMensaje(mensaje);
            n.setUrlDestino(urlDestino);
            n.setLeida(false);
            n.setEntregada(false);
            n.setFechaCreacion(LocalDateTime.now());
            n.setEstado("ACTIVO");
            return notificacionDao.save(n);
        }).collect(Collectors.toList());

        log.info("📣 Comunicado #{} enviado por {} → {} destinatario(s) [{}]",
            guardado.getIdComunicado(),
            emisor != null ? emisor.getUsuario() : "?",
            creadas.size(), alcanceDetalle);

        // 4) Push SSE a los conectados (los desconectados lo verán al entrar).
        creadas.forEach(n -> enviarSse(n, importante));

        return guardado;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Comunicado> listarComunicados(Pageable pageable) {
        return comunicadoDao.findAllByOrderByFechaEnvioDesc(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public long[] resumenComunicado(Comunicado comunicado) {
        return new long[] {
            notificacionDao.countByComunicado(comunicado),
            notificacionDao.countByComunicadoAndEntregadaTrue(comunicado),
            notificacionDao.countByComunicadoAndLeidaTrue(comunicado)
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, long[]> resumenComunicadoLote(List<Long> idsComunicado) {
        Map<Long, long[]> mapa = new HashMap<>();
        if (idsComunicado == null || idsComunicado.isEmpty()) return mapa;
        for (Object[] fila : notificacionDao.resumenPorComunicado(idsComunicado)) {
            Long id        = (Long) fila[0];
            long entregadas = fila[1] != null ? ((Number) fila[1]).longValue() : 0L;
            long leidas     = fila[2] != null ? ((Number) fila[2]).longValue() : 0L;
            mapa.put(id, new long[]{ entregadas, leidas });
        }
        return mapa;
    }

    @Override
    @Transactional(readOnly = true)
    public ComunicadoDetalleDto detalleComunicado(Long idComunicado) {
        Comunicado com = comunicadoDao.findById(idComunicado)
            .orElseThrow(() -> new IllegalArgumentException("Comunicado no encontrado."));

        List<Notificacion> destinos =
            notificacionDao.findByComunicadoOrderByLeidaAscFechaCreacionDesc(com);

        long total      = destinos.size();
        long recibieron = destinos.stream().filter(Notificacion::isEntregada).count();
        long leyeron    = destinos.stream().filter(Notificacion::isLeida).count();
        long pendientes = total - recibieron;
        int  pct        = total > 0 ? (int) Math.round(leyeron * 100.0 / total) : 0;

        List<ComunicadoDetalleDto.Destinatario> lista = destinos.stream()
            .map(n -> ComunicadoDetalleDto.Destinatario.builder()
                .nombre(nombreDe(n.getUsuario()))
                .usuario(n.getUsuario() != null ? n.getUsuario().getUsuario() : "—")
                .entregada(n.isEntregada())
                .fechaEntrega(n.getFechaEntrega() != null ? n.getFechaEntrega().format(FMT) : null)
                .leida(n.isLeida())
                .fechaLectura(n.getFechaLectura() != null ? n.getFechaLectura().format(FMT) : null)
                .build())
            .collect(Collectors.toList());

        return ComunicadoDetalleDto.builder()
            .idComunicado(com.getIdComunicado())
            .tipo(com.getTipo() != null ? com.getTipo().name() : null)
            .titulo(com.getTitulo())
            .mensaje(com.getMensaje())
            .urlDestino(com.getUrlDestino())
            .importante(com.isImportante())
            .alcance(com.getAlcance() != null ? com.getAlcance().name() : null)
            .alcanceDetalle(com.getAlcanceDetalle())
            .emisor(nombreDe(com.getEmisor()))
            .fechaEnvio(com.getFechaEnvio() != null ? com.getFechaEnvio().format(FMT) : null)
            .totalDestinatarios((int) total)
            .recibieron(recibieron)
            .leyeron(leyeron)
            .pendientes(pendientes)
            .porcentajeLectura(pct)
            .destinatarios(lista)
            .build();
    }

    // =========================================================================
    //  PRIVADOS
    // =========================================================================

    /** Empuja una notificación por SSE al destinatario si está conectado. */
    private void enviarSse(Notificacion n, boolean importante) {
        if (n.getUsuario() == null) return;
        Long idUsuario = n.getUsuario().getIdUsuario();
        if (!sseRegistry.isUsuarioConectado(idUsuario)) return;

        long noLeidas = notificacionDao.countByUsuarioAndLeidaFalse(n.getUsuario());

        NotificacionSseDto dto = NotificacionSseDto.builder()
            .idNotificacion(n.getIdNotificacion())
            .tipo(n.getTipo() != null ? n.getTipo().name() : null)
            .titulo(n.getTitulo())
            .mensaje(n.getMensaje())
            .referenciaId(n.getReferenciaId())
            .urlDestino(n.getUrlDestino())
            .fechaCreacion(n.getFechaCreacion() != null ? n.getFechaCreacion().format(FMT) : null)
            .noLeidasTotal(noLeidas)
            .importante(importante)
            .build();

        sseRegistry.enviarAUsuario(idUsuario, "notificacion", dto);
    }

    private String nombreDe(Usuario u) {
        if (u == null) return "—";
        if (u.getPersona() != null && u.getPersona().getNombreCompleto() != null) {
            return u.getPersona().getNombreCompleto();
        }
        return u.getUsuario() != null ? u.getUsuario() : "—";
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
