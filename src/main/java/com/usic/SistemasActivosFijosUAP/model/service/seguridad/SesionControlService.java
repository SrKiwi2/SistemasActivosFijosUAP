package com.usic.SistemasActivosFijosUAP.model.service.seguridad;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.IService.INotificacionService;
import com.usic.SistemasActivosFijosUAP.model.dao.ISesionUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.NotificacionSseDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.SesionUsuario;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;

import jakarta.servlet.http.HttpSession;

/**
 * Control de sesiones por equipo: quién tiene sesión abierta, dónde, desde cuándo, y
 * cerrarlas a distancia ("Mis sesiones abiertas", el administrador, cambio de contraseña,
 * usuario desactivado...).
 *
 * <p>Cerrar una sesión hace dos cosas: la marca CERRADA en la base (así el "recordarme" de
 * ese equipo deja de valer, también tras un reinicio) y anota su id en memoria, que
 * {@code SesionControlInterceptor} consulta en cada petición sin ir a la base para cortar la
 * sesión HTTP que todavía esté viva en ese equipo.
 *
 * <p>No depende de SesionPermisosService (que sí depende de este): la parte que arma una
 * sesión HTTP a partir de la cookie vive en RecordarmeService.
 */
@Service
public class SesionControlService {

    private static final Logger log = LoggerFactory.getLogger(SesionControlService.class);

    /** id de SesionUsuario de esta sesión HTTP. */
    public static final String ATTR_ID = "sesion_usuario_id";
    /** Boolean: la sesión es "recordada" (no se cierra por inactividad). */
    public static final String ATTR_RECORDADA = "sesion_recordada";
    /** Motivo que el listener anota al destruirse la sesión HTTP (si no, VENCIDA). */
    public static final String ATTR_MOTIVO = "sesion_motivo_cierre";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ISesionUsuarioDao dao;
    private final SesionInactividadService inactividad;
    private final INotificacionService notificaciones;
    private final SseEmitterRegistry sse;

    /** id de sesión cerrada → instante en que se cerró. */
    private final Map<Long, Long> cerradas = new ConcurrentHashMap<>();

    public SesionControlService(ISesionUsuarioDao dao, SesionInactividadService inactividad,
            INotificacionService notificaciones, SseEmitterRegistry sse) {
        this.dao = dao;
        this.inactividad = inactividad;
        this.notificaciones = notificaciones;
        this.sse = sse;
    }

    // ── Alta ───────────────────────────────────────────────────────────────

    /**
     * Registra la sesión recién iniciada. Si el usuario ya tenía sesiones y este equipo
     * nunca había entrado con su cuenta, le avisa (notificación + aviso en vivo).
     *
     * <p>Sin @Transactional a propósito: el registro se guarda solo (save) y el aviso va
     * aparte, así una falla del aviso no se lleva puesta la sesión registrada.
     */
    public SesionUsuario registrar(Usuario usuario, boolean recordar, String serie, String tokenHash,
            LocalDateTime expira, String equipo, String ip, String userAgent, String idSesionHttp) {
        Long idUsuario = usuario.getIdUsuario();
        boolean equipoNuevo = equipo != null
                && dao.existsByUsuarioIdUsuario(idUsuario)
                && !dao.existsByUsuarioIdUsuarioAndEquipo(idUsuario, equipo);

        LocalDateTime ahora = LocalDateTime.now();
        SesionUsuario s = new SesionUsuario();
        s.setUsuario(usuario);
        s.setSerie(serie);
        s.setTokenHash(tokenHash);
        s.setRecordar(recordar);
        s.setExpira(expira);
        s.setEquipo(equipo);
        s.setDispositivo(describirEquipo(userAgent));
        s.setIp(recortar(ip, 45));
        s.setUserAgent(recortar(userAgent, 512));
        s.setIdSesionHttp(idSesionHttp);
        s.setCreado(ahora);
        s.setUltimoUso(ahora);
        s.setEstado(SesionUsuario.ACTIVA);
        dao.save(s);

        if (equipoNuevo) {
            avisarEquipoNuevo(usuario, s);
        }
        return s;
    }

    private void avisarEquipoNuevo(Usuario usuario, SesionUsuario s) {
        try {
            String titulo = "Ingreso a su cuenta desde un equipo nuevo";
            String mensaje = "Se inició sesión con su usuario desde " + s.getDispositivo()
                    + (s.getIp() != null ? " (IP " + s.getIp() + ")" : "")
                    + " el " + s.getCreado().format(FMT) + ". Si no fue usted, cierre esa sesión en"
                    + " «Mis sesiones abiertas» (menú de su nombre) y cambie su contraseña.";
            Notificacion n = notificaciones.crear(usuario, Notificacion.TipoNotificacion.SISTEMA,
                    titulo, mensaje, "sesion-" + s.getIdSesionUsuario(), null);
            NotificacionSseDto dto = NotificacionSseDto.builder()
                    .idNotificacion(n.getIdNotificacion())
                    .tipo(n.getTipo().name())
                    .titulo(titulo)
                    .mensaje(mensaje)
                    .referenciaId(n.getReferenciaId())
                    .fechaCreacion(s.getCreado().format(FMT))
                    .noLeidasTotal(notificaciones.contarNoLeidas(usuario))
                    .importante(true)
                    .build();
            sse.enviarAUsuario(usuario.getIdUsuario(), "notificacion", dto);
        } catch (Exception e) {
            // El aviso nunca puede impedir el inicio de sesión.
            log.warn("No se pudo avisar del equipo nuevo a {}: {}", usuario.getUsuario(), e.getMessage());
        }
    }

    // ── Uso ────────────────────────────────────────────────────────────────

    /**
     * Último uso, IP y navegador (y, si es recordada, nueva fecha de vencimiento).
     * Devuelve false si la sesión ya no está ACTIVA en la base: la cerraron desde otro
     * lado y este servidor no se enteró (reinicio, otra instancia).
     */
    @Transactional
    public boolean tocar(Long id, String idSesionHttp, LocalDateTime nuevaExpira, String ip, String userAgent) {
        return dao.tocar(id, LocalDateTime.now(), nuevaExpira, idSesionHttp, recortar(ip, 45),
                describirEquipo(userAgent)) > 0;
    }

    @Transactional(readOnly = true)
    public SesionUsuario buscarPorSerie(String serie) {
        return serie == null ? null : dao.buscarPorSerie(serie).orElse(null);
    }

    /** ¿Esta sesión fue cerrada a distancia? Solo memoria: corre en cada petición. */
    public boolean cerradaADistancia(Long id) {
        return id != null && cerradas.containsKey(id);
    }

    // ── Cierre ─────────────────────────────────────────────────────────────

    /** Cierre "normal" de la propia sesión (cerrar sesión, inactividad, vencimiento). */
    @Transactional
    public void cerrar(Long id, String motivo) {
        if (id == null) return;
        dao.cerrar(List.of(id), motivo, null, LocalDateTime.now());
    }

    /** Cierra una sesión de otro equipo: también corta la sesión HTTP que siga viva allí. */
    @Transactional
    public void cerrarADistancia(Long id, String motivo, Long actor) {
        if (id == null) return;
        int n = dao.cerrar(List.of(id), motivo, actor, LocalDateTime.now());
        if (n > 0) anotarCerradas(List.of(id));
    }

    /**
     * Cierra todas las sesiones del usuario, menos {@code excepto} (la del que hace el
     * cambio, si debe seguir). Devuelve cuántas se cerraron.
     */
    @Transactional
    public int cerrarTodas(Long idUsuario, Long excepto, String motivo, Long actor) {
        List<Long> ids = dao.idsActivasDe(idUsuario, excepto != null ? excepto : -1L);
        if (ids.isEmpty()) return 0;
        int n = dao.cerrar(ids, motivo, actor, LocalDateTime.now());
        anotarCerradas(ids);
        return n;
    }

    /**
     * Cambio de contraseña: los equipos con la sesión mantenida no pueden seguir entrando
     * con la contraseña vieja, aunque no se haya pedido cerrar las demás sesiones.
     */
    @Transactional
    public int cerrarRecordadas(Long idUsuario, Long excepto, String motivo, Long actor) {
        List<Long> ids = dao.idsRecordadasDe(idUsuario, excepto != null ? excepto : -1L);
        if (ids.isEmpty()) return 0;
        int n = dao.cerrar(ids, motivo, actor, LocalDateTime.now());
        anotarCerradas(ids);
        return n;
    }

    private void anotarCerradas(List<Long> ids) {
        // Recién al confirmar: si la transacción se deshace, la sesión no se cerró.
        despuesDeConfirmar(() -> {
            long ahora = System.currentTimeMillis();
            ids.forEach(i -> cerradas.put(i, ahora));
        });
    }

    /** Lo llama el listener cuando Tomcat destruye la sesión HTTP. */
    public void alDestruirse(HttpSession session) {
        Object id;
        Object recordada;
        Object motivo;
        try {
            id = session.getAttribute(ATTR_ID);
            recordada = session.getAttribute(ATTR_RECORDADA);
            motivo = session.getAttribute(ATTR_MOTIVO);
        } catch (IllegalStateException e) {
            return;
        }
        if (!(id instanceof Long idSesion)) return;
        // Una recordada sigue abierta en ese equipo aunque se haya perdido la sesión HTTP
        // (navegador cerrado, reinicio): la cookie la vuelve a armar. Solo se cierra si
        // hubo un motivo explícito (inactividad no aplica a las recordadas).
        if (Boolean.TRUE.equals(recordada) && motivo == null) return;
        try {
            cerrar(idSesion, motivo instanceof String m ? m : "VENCIDA");
        } catch (Exception e) {
            log.warn("No se pudo cerrar la sesión {}: {}", idSesion, e.getMessage());
        }
    }

    // ── Mis sesiones abiertas ──────────────────────────────────────────────

    /** El usuario cierra una de SUS sesiones en otro equipo. */
    @Transactional
    public void cerrarPropia(Long idUsuario, Long idSesion, Long idActual) {
        SesionUsuario s = obtener(idSesion);
        if (s == null || !idUsuario.equals(s.getUsuario().getIdUsuario())
                || !SesionUsuario.ACTIVA.equals(s.getEstado())) {
            throw new ReglaNegocioException("Esa sesión ya no está abierta.");
        }
        if (s.getIdSesionUsuario().equals(idActual)) {
            throw new ReglaNegocioException("Esta es la sesión que está usando: ciérrela con «Cerrar sesión».");
        }
        cerrarADistancia(idSesion, "CERRADA_POR_USUARIO", idUsuario);
    }

    // ── Consulta ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> abiertas(Long idUsuario, Long idActual) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (SesionUsuario s : dao.abiertasDe(idUsuario, desdeNoRecordadas())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getIdSesionUsuario());
            m.put("dispositivo", s.getDispositivo());
            m.put("ip", s.getIp());
            m.put("inicio", s.getCreado() != null ? s.getCreado().format(FMT) : null);
            m.put("ultimoUso", s.getUltimoUso() != null ? s.getUltimoUso().format(FMT) : null);
            m.put("recordada", s.isRecordar());
            m.put("vence", s.isRecordar() && s.getExpira() != null ? s.getExpira().format(FMT) : null);
            m.put("actual", s.getIdSesionUsuario().equals(idActual));
            lista.add(m);
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public SesionUsuario obtener(Long id) {
        return id == null ? null : dao.findById(id).orElse(null);
    }

    /** Una no recordada sin uso en este tiempo ya no está abierta (el servidor la habría cerrado). */
    private LocalDateTime desdeNoRecordadas() {
        return LocalDateTime.now().minusSeconds(inactividad.getLimiteMs() / 1000 + 600);
    }

    // ── Limpieza ───────────────────────────────────────────────────────────

    /** Todos los días: recordadas vencidas, no recordadas abandonadas y la memoria de cierres. */
    @Scheduled(cron = "0 40 3 * * *")
    @Transactional
    public void limpiar() {
        int n = dao.cerrarVencidas(LocalDateTime.now(), LocalDateTime.now().minusDays(1));
        long hace1Dia = System.currentTimeMillis() - 86_400_000L;
        cerradas.values().removeIf(t -> t < hace1Dia);
        if (n > 0) log.info("Sesiones vencidas cerradas: {}", n);
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    private void despuesDeConfirmar(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    private static String recortar(String texto, int max) {
        return texto != null && texto.length() > max ? texto.substring(0, max) : texto;
    }

    /** "Chrome en Windows", "Safari en iPhone"... a partir del User-Agent. */
    public static String describirEquipo(String ua) {
        if (ua == null || ua.isBlank()) return "Equipo desconocido";
        String navegador;
        if (ua.contains("Edg/")) navegador = "Edge";
        else if (ua.contains("OPR/") || ua.contains("Opera")) navegador = "Opera";
        else if (ua.contains("Firefox/")) navegador = "Firefox";
        else if (ua.contains("Chrome/") || ua.contains("CriOS/")) navegador = "Chrome";
        else if (ua.contains("Safari/")) navegador = "Safari";
        else navegador = "Navegador";
        String so;
        if (ua.contains("Android")) so = "Android";
        else if (ua.contains("iPhone")) so = "iPhone";
        else if (ua.contains("iPad")) so = "iPad";
        else if (ua.contains("Windows")) so = "Windows";
        else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) so = "Mac";
        else if (ua.contains("Linux")) so = "Linux";
        else so = "otro sistema";
        return navegador + " en " + so;
    }
}
