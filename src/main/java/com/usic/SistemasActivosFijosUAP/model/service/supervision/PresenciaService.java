package com.usic.SistemasActivosFijosUAP.model.service.supervision;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Quién está conectado y qué pantalla está mirando, en vivo.
 *
 * <p>Cada navegador con sesión avisa (sciaf-presencia.js → POST /api/presencia) cuando
 * cambia de pantalla, cada 30 s mientras está abierto, y cuando la pestaña del navegador
 * pasa a segundo plano. Acá se guarda la última foto de cada sesión, en memoria: no toca la
 * base, y un reinicio la vacía (los navegadores vuelven a aparecer en su próximo aviso).
 *
 * <p>Una sesión deja de figurar cuando se cierra (logout, cierre forzado, vencimiento: ver
 * {@code PresenciaSesionListener}) o si pasan {@link #VENCE} sin avisos (navegador cerrado
 * de golpe, sin red).
 *
 * <p>El id de sesión real nunca sale de acá: hacia la pantalla viaja un id propio al azar.
 */
@Service
public class PresenciaService {

    /** Sin avisos durante este tiempo, la sesión se da por desconectada. */
    static final Duration VENCE = Duration.ofSeconds(100);
    /** Sin teclado ni mouse durante este tiempo, figura "ausente". */
    static final Duration AUSENTE = Duration.ofMinutes(5);
    private static final int RECORRIDO_MAX = 20;

    private final Map<String, Presencia> porSesion = new ConcurrentHashMap<>();

    /** Lo que el navegador informa en cada aviso. */
    public record Aviso(String url, String titulo, String icono, List<String> pestanas,
            boolean visible, Long inactivoSeg) {}

    public void registrar(HttpServletRequest request, Aviso aviso) {
        HttpSession session = request.getSession(false);
        Usuario u = RolesSciaf.usuarioDe(request);
        if (session == null || u == null) return;

        Instant ahora = Instant.now();
        Presencia p = porSesion.computeIfAbsent(session.getId(), k -> {
            Presencia n = new Presencia();
            n.id = UUID.randomUUID().toString().substring(0, 12);
            n.inicioSesion = Instant.ofEpochMilli(session.getCreationTime());
            return n;
        });
        synchronized (p) {
            p.idUsuario = u.getIdUsuario();
            p.usuario = u.getUsuario();
            Persona per = u.getPersona();
            p.nombre = per != null ? per.getNombreCompleto() : u.getUsuario();
            p.rol = RolesSciaf.rolDe(u);
            p.ip = ipDe(request);
            p.navegador = resumirNavegador(request.getHeader("User-Agent"));
            p.ultimoAviso = ahora;
            p.visible = aviso.visible();
            long inactivo = aviso.inactivoSeg() != null ? Math.max(0, aviso.inactivoSeg()) : 0;
            p.ultimaInteraccion = ahora.minusSeconds(inactivo);
            p.pestanas = aviso.pestanas() != null
                    ? aviso.pestanas().stream().limit(15).map(t -> recortar(t, 60)).toList() : List.of();

            String url = recortar(aviso.url(), 200);
            if (url != null && !url.equals(p.url)) {
                p.url = url;
                p.desdeVista = ahora;
                p.recorrido.addFirst(new Paso(recortar(aviso.titulo(), 80), url, ahora));
                while (p.recorrido.size() > RECORRIDO_MAX) p.recorrido.removeLast();
            }
            p.titulo = recortar(aviso.titulo(), 80);
            p.icono = recortar(aviso.icono(), 60);
        }
    }

    /** La sesión terminó: deja de figurar. */
    public void quitar(String sessionId) {
        if (sessionId != null) porSesion.remove(sessionId);
    }

    /** Lo que muestra la pantalla de usuarios conectados, los más activos primero. */
    public List<Map<String, Object>> listar() {
        Instant ahora = Instant.now();
        List<Presencia> vivas = new ArrayList<>(porSesion.values());
        vivas.removeIf(p -> p.ultimoAviso == null || Duration.between(p.ultimoAviso, ahora).compareTo(VENCE) > 0);
        vivas.sort(Comparator.comparing((Presencia p) -> estado(p, ahora))
                .thenComparing(p -> p.nombre == null ? "" : p.nombre));

        List<Map<String, Object>> r = new ArrayList<>();
        for (Presencia p : vivas) {
            synchronized (p) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.id);
                m.put("idUsuario", p.idUsuario);
                m.put("usuario", p.usuario);
                m.put("nombre", p.nombre);
                m.put("rol", p.rol);
                m.put("ip", p.ip);
                m.put("navegador", p.navegador);
                m.put("estado", estado(p, ahora));
                m.put("vista", p.titulo);
                m.put("url", p.url);
                m.put("icono", p.icono);
                m.put("enVistaSeg", p.desdeVista != null ? Duration.between(p.desdeVista, ahora).toSeconds() : null);
                m.put("inactivoSeg", p.ultimaInteraccion != null ? Duration.between(p.ultimaInteraccion, ahora).toSeconds() : null);
                m.put("conectadoSeg", p.inicioSesion != null ? Duration.between(p.inicioSesion, ahora).toSeconds() : null);
                m.put("ultimoAvisoSeg", Duration.between(p.ultimoAviso, ahora).toSeconds());
                m.put("pestanas", p.pestanas);
                List<Map<String, Object>> rec = new ArrayList<>();
                for (Paso paso : p.recorrido) {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("titulo", paso.titulo());
                    x.put("url", paso.url());
                    x.put("haceSeg", Duration.between(paso.cuando(), ahora).toSeconds());
                    rec.add(x);
                }
                m.put("recorrido", rec);
                r.add(m);
            }
        }
        return r;
    }

    /** idUsuario de una entrada (para cerrar su sesión desde el monitor). */
    public Long usuarioDe(String id) {
        return porSesion.values().stream().filter(p -> p.id.equals(id)).map(p -> p.idUsuario).findFirst().orElse(null);
    }

    /** 1 = en línea, 2 = ausente (sin usar teclado/mouse o pestaña en segundo plano). */
    private int estado(Presencia p, Instant ahora) {
        boolean quieto = p.ultimaInteraccion != null
                && Duration.between(p.ultimaInteraccion, ahora).compareTo(AUSENTE) > 0;
        return (!p.visible || quieto) ? 2 : 1;
    }

    /** Limpieza de lo vencido, para que el mapa no crezca con navegadores cerrados de golpe. */
    @Scheduled(fixedDelay = 120_000)
    public void limpiar() {
        Instant limite = Instant.now().minus(VENCE.multipliedBy(3));
        porSesion.values().removeIf(p -> p.ultimoAviso == null || p.ultimoAviso.isBefore(limite));
    }

    private static String ipDe(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private static String resumirNavegador(String ua) {
        if (ua == null) return null;
        String so = ua.contains("Android") ? "Android" : ua.contains("iPhone") ? "iPhone"
                : ua.contains("Windows") ? "Windows" : ua.contains("Mac OS") ? "Mac" : ua.contains("Linux") ? "Linux" : "";
        String nav = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari" : "Otro";
        return nav + (so.isEmpty() ? "" : " · " + so);
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private record Paso(String titulo, String url, Instant cuando) {}

    private static class Presencia {
        String id;
        Long idUsuario;
        String usuario, nombre, rol, ip, navegador;
        String url, titulo, icono;
        List<String> pestanas = List.of();
        boolean visible = true;
        Instant inicioSesion, ultimoAviso, ultimaInteraccion, desdeVista;
        final Deque<Paso> recorrido = new ArrayDeque<>();
    }
}
