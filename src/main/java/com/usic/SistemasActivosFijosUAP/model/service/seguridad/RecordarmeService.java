package com.usic.SistemasActivosFijosUAP.model.service.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.LogAccesoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.SesionUsuario;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * "Mantener la sesión iniciada en este equipo".
 *
 * <p>Al ingresar con la casilla marcada, el navegador recibe la cookie
 * {@value #COOKIE_RECORDAR} = {@code serie:token}. Cuando llega un pedido sin sesión pero
 * con esa cookie (cerró el navegador, se reinició el servidor), se arma la sesión de nuevo
 * sin pedir la contraseña, siempre que esa sesión siga ACTIVA en {@code sesion_usuario}, no
 * haya vencido y el usuario siga activo.
 *
 * <p>Seguridad: la cookie es HttpOnly (el JavaScript de la página no la ve), de ella solo
 * se guarda el hash, y cada equipo se puede cerrar a distancia. El token NO cambia en cada
 * uso a propósito: hacerlo obliga a que el navegador reciba siempre la cookie nueva, y una
 * respuesta perdida o dos pedidos simultáneos dejaban afuera al usuario legítimo (justo
 * lo que este cambio quiere evitar). En su lugar, la sesión muestra la IP y el navegador
 * del último uso, y un equipo nuevo con contraseña dispara un aviso.
 *
 * <p>Además, cada navegador lleva {@value #COOKIE_EQUIPO} (un identificador sin datos) para
 * reconocer equipos nuevos y avisarle al usuario.
 */
@Service
public class RecordarmeService {

    private static final Logger log = LoggerFactory.getLogger(RecordarmeService.class);

    public static final String COOKIE_RECORDAR = "SCIAF_RECORDAR";
    public static final String COOKIE_EQUIPO = "SCIAF_EQUIPO";

    /** Valor vigente de la cookie, para renovarle el vencimiento mientras se usa. */
    private static final String ATTR_COOKIE = "sesion_cookie_recordar";
    /** Última vez (ms) que se anotó el uso en la base. */
    private static final String ATTR_TOQUE = "sesion_ultimo_toque";

    private static final long TOQUE_MS = 5 * 60_000L;
    /** Los navegadores no guardan cookies por más de 400 días. */
    private static final int EQUIPO_SEG = 400 * 86_400;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final SesionControlService control;
    private final SesionPermisosService permisos;
    private final SesionInactividadService inactividad;
    private final IUsuarioDao usuarioDao;
    private final LogAccesoService logAcceso;
    /** Días hábiles (lunes a viernes) sin uso tras los que vuelve a pedir la contraseña. */
    private final int diasHabiles;

    public RecordarmeService(SesionControlService control, SesionPermisosService permisos,
            SesionInactividadService inactividad, IUsuarioDao usuarioDao, LogAccesoService logAcceso,
            @Value("${sciaf.sesion.recordar-dias-habiles:5}") int diasHabiles) {
        this.control = control;
        this.permisos = permisos;
        this.inactividad = inactividad;
        this.usuarioDao = usuarioDao;
        this.logAcceso = logAcceso;
        this.diasHabiles = Math.max(1, diasHabiles);
    }

    // ── Inicio de sesión con contraseña ────────────────────────────────────

    /**
     * Registra la sesión de este equipo (la sesión HTTP ya tiene usuario y permisos) y,
     * si se pidió, deja la cookie para mantenerla.
     */
    public void abrir(HttpServletRequest request, HttpServletResponse response, HttpSession session,
            Usuario usuario, boolean recordar) {
        String equipo = equipo(request, response);
        String serie = aleatorio(18);
        String token = recordar ? aleatorio(32) : null;
        LocalDateTime expira = recordar ? vencimiento(LocalDateTime.now()) : null;

        SesionUsuario s = control.registrar(usuario, recordar, serie, token != null ? sha256(token) : null,
                expira, equipo, ipDe(request), request.getHeader("User-Agent"), session.getId());
        marcar(session, s.getIdSesionUsuario(), recordar);

        if (recordar) {
            String valor = serie + ":" + token;
            session.setAttribute(ATTR_COOKIE, valor);
            escribir(request, response, COOKIE_RECORDAR, valor, segundosCookie(expira));
        } else {
            olvidar(request, response);
        }
    }

    // ── Sesión rearmada desde la cookie ────────────────────────────────────

    /** ¿El pedido trae la cookie de "recordarme"? */
    public boolean traeCookie(HttpServletRequest request) {
        return leer(request, COOKIE_RECORDAR) != null;
    }

    /**
     * Pedido sin sesión con la cookie de "recordarme": si sigue valiendo, arma la sesión
     * (la misma petición ya la ve con {@code request.getSession(false)}). Si no vale, borra
     * la cookie. Devuelve si la armó.
     */
    public boolean restaurar(HttpServletRequest request, HttpServletResponse response) {
        String valor = leer(request, COOKIE_RECORDAR);
        if (valor == null) return false;
        int dos = valor.indexOf(':');
        if (dos <= 0 || dos == valor.length() - 1) {
            olvidar(request, response);
            return false;
        }
        String serie = valor.substring(0, dos);
        String hash = sha256(valor.substring(dos + 1));

        SesionUsuario s = control.buscarPorSerie(serie);
        LocalDateTime ahora = LocalDateTime.now();
        if (s == null || !SesionUsuario.ACTIVA.equals(s.getEstado()) || !s.isRecordar()
                || s.getExpira() == null || s.getExpira().isBefore(ahora)) {
            olvidar(request, response);
            return false;
        }

        if (!iguales(hash, s.getTokenHash())) {
            // Serie conocida con un token que no es el suyo: cookie inventada o alterada.
            log.warn("Cookie de sesión recordada con token inválido (sesión {}, IP {})",
                    s.getIdSesionUsuario(), ipDe(request));
            olvidar(request, response);
            return false;
        }

        Usuario u = usuarioDao.findByIdConPersonaYRol(s.getUsuario().getIdUsuario()).orElse(null);
        if (u == null || !"ACTIVO".equals(u.getEstado())) {
            control.cerrarADistancia(s.getIdSesionUsuario(), "USUARIO_INACTIVO", null);
            olvidar(request, response);
            return false;
        }

        // Sesión nueva (si había una anónima, se descarta: id nuevo, sin fijación de sesión).
        HttpSession anonima = request.getSession(false);
        if (anonima != null) {
            try {
                anonima.invalidate();
            } catch (IllegalStateException yaInvalida) {
                // nada
            }
        }
        HttpSession session = request.getSession(true);
        permisos.iniciar(session, u);
        inactividad.iniciar(session);
        marcar(session, s.getIdSesionUsuario(), true);
        session.setAttribute(ATTR_COOKIE, valor);
        // Vencimiento corrido: otros N días hábiles desde hoy (en la base y en la cookie).
        LocalDateTime expira = vencimiento(ahora);
        escribir(request, response, COOKIE_RECORDAR, valor, segundosCookie(expira));
        control.tocar(s.getIdSesionUsuario(), session.getId(), expira, ipDe(request),
                request.getHeader("User-Agent"));
        session.setAttribute(ATTR_TOQUE, System.currentTimeMillis());

        try {
            logAcceso.registrar(u.getUsuario(), u.getIdUsuario(), RolesSciaf.rolDe(u), true, "LOGIN_RECORDADO",
                    ipDe(request), request.getHeader("User-Agent"), session.getId());
        } catch (Exception e) {
            log.warn("No se pudo registrar el acceso recordado de {}: {}", u.getUsuario(), e.getMessage());
        }
        return true;
    }

    // ── Uso ────────────────────────────────────────────────────────────────

    /**
     * Anota el uso de la sesión (cada 5 min como mucho, no en cada pedido). Las recordadas
     * corren su vencimiento: vencen a los {@code diasHabiles} días hábiles SIN USO.
     *
     * @return false si la sesión ya está cerrada en la base (la cerraron desde otro lado y
     *         este servidor no se enteró, por ejemplo tras un reinicio): hay que cortarla.
     */
    public boolean tocar(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        Object ultimo = session.getAttribute(ATTR_TOQUE);
        long ahora = System.currentTimeMillis();
        if (ultimo instanceof Long t && ahora - t < TOQUE_MS) return true;
        session.setAttribute(ATTR_TOQUE, ahora);

        Object id = session.getAttribute(SesionControlService.ATTR_ID);
        if (!(id instanceof Long idSesion)) return true;
        boolean recordada = inactividad.recordada(session);
        boolean abierta;
        LocalDateTime expira = recordada ? vencimiento(LocalDateTime.now()) : null;
        try {
            abierta = control.tocar(idSesion, session.getId(), expira,
                    ipDe(request), request.getHeader("User-Agent"));
        } catch (Exception e) {
            // Sin base no se sabe: no se saca a nadie por una falla de conexión.
            log.debug("No se pudo anotar el uso de la sesión {}: {}", idSesion, e.getMessage());
            return true;
        }
        if (abierta && recordada && session.getAttribute(ATTR_COOKIE) instanceof String valor) {
            escribir(request, response, COOKIE_RECORDAR, valor, segundosCookie(expira));
        }
        return abierta;
    }

    // ── Salida ─────────────────────────────────────────────────────────────

    /** Cerrar sesión: la de este equipo se cierra en la base y se borra la cookie. */
    public void cerrar(HttpServletRequest request, HttpServletResponse response, HttpSession session, String motivo) {
        if (session != null) {
            try {
                Object id = session.getAttribute(SesionControlService.ATTR_ID);
                session.setAttribute(SesionControlService.ATTR_MOTIVO, motivo);
                if (id instanceof Long idSesion) control.cerrar(idSesion, motivo);
            } catch (IllegalStateException yaInvalida) {
                // nada
            }
        }
        // Sin sesión HTTP (venció) pero con cookie: también se cierra esa sesión recordada.
        String valor = leer(request, COOKIE_RECORDAR);
        if (valor != null && valor.indexOf(':') > 0) {
            SesionUsuario s = control.buscarPorSerie(valor.substring(0, valor.indexOf(':')));
            if (s != null && iguales(sha256(valor.substring(valor.indexOf(':') + 1)), s.getTokenHash())) {
                control.cerrar(s.getIdSesionUsuario(), motivo);
            }
        }
        olvidar(request, response);
    }

    public void olvidar(HttpServletRequest request, HttpServletResponse response) {
        if (leer(request, COOKIE_RECORDAR) != null) {
            escribir(request, response, COOKIE_RECORDAR, "", 0);
        }
    }

    // ── Utilidades ─────────────────────────────────────────────────────────

    /**
     * {@code desde} + N días hábiles, a la misma hora: sábado y domingo no cuentan. Usado un
     * viernes a las 15:00 con 5 días, vence el viernes siguiente a las 15:00; usado un
     * sábado, cuenta desde el lunes. No descuenta feriados.
     */
    LocalDateTime vencimiento(LocalDateTime desde) {
        LocalDateTime f = desde;
        int contados = 0;
        while (contados < diasHabiles) {
            f = f.plusDays(1);
            DayOfWeek d = f.getDayOfWeek();
            if (d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY) contados++;
        }
        return f;
    }

    /** La cookie dura hasta el vencimiento y un día más: el que decide si vale es el servidor. */
    private static int segundosCookie(LocalDateTime expira) {
        return (int) Math.max(86_400, Duration.between(LocalDateTime.now(), expira).getSeconds() + 86_400);
    }

    private void marcar(HttpSession session, Long idSesion, boolean recordada) {
        session.setAttribute(SesionControlService.ATTR_ID, idSesion);
        session.setAttribute(SesionControlService.ATTR_RECORDADA, recordada);
    }

    private String equipo(HttpServletRequest request, HttpServletResponse response) {
        String equipo = leer(request, COOKIE_EQUIPO);
        if (equipo == null || equipo.length() > 40) {
            equipo = aleatorio(18);
        }
        escribir(request, response, COOKIE_EQUIPO, equipo, EQUIPO_SEG);
        return equipo;
    }

    private static String leer(HttpServletRequest request, String nombre) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (nombre.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue();
            }
        }
        return null;
    }

    private static void escribir(HttpServletRequest request, HttpServletResponse response, String nombre,
            String valor, int maxAgeSeg) {
        if (response.isCommitted()) return;
        ResponseCookie c = ResponseCookie.from(nombre, valor)
                .httpOnly(true)
                .secure(request.isSecure())
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAgeSeg)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, c.toString());
    }

    private static String aleatorio(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String texto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comparación en tiempo constante. */
    private static boolean iguales(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String ipDe(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
