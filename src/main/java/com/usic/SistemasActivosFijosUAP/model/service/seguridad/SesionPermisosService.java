package com.usic.SistemasActivosFijosUAP.model.service.seguridad;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpSession;

/**
 * Mantiene al día los permisos de menú de las sesiones abiertas, sin que nadie tenga
 * que cerrar sesión.
 *
 * <p>Antes, {@code session.opciones} se calculaba una sola vez al iniciar sesión: si el
 * administrador cambiaba los permisos de alguien, o el menú, el cambio recién se veía al
 * volver a entrar. Ahora cada sesión guarda la "versión" de permisos con la que se armó, y
 * {@code SesionPermisosInterceptor} la compara en cada petición: si cambió, la vuelve a
 * leer de la base antes de atender. Al mismo tiempo se avisa por SSE al navegador, que
 * pide el menú nuevo y lo reemplaza sin recargar la página (sciaf-menu-vivo.js).
 *
 * <p>Versiones: una global (cambios del catálogo de menú, que afectan a todos) y una por
 * usuario (sus permisos, su rol, su estado). Viven en memoria: un reinicio deja todas en
 * cero, y las sesiones viejas igual no sobreviven a un reinicio.
 */
@Service
public class SesionPermisosService {

    private static final Logger log = LoggerFactory.getLogger(SesionPermisosService.class);

    public static final String ATTR_VERSION = "permisos_version";
    /** Momento (ms) desde el que la sesión es válida aunque sea más vieja: ver forzarCierre. */
    public static final String ATTR_RENOVADA = "sesion_renovada";

    public static final String EVENTO_PERMISOS = "permisos";
    public static final String EVENTO_MENU = "menu";
    public static final String EVENTO_PERFIL = "perfil";

    private final AtomicLong versionGlobal = new AtomicLong(System.currentTimeMillis());
    private final Map<Long, Long> versionPorUsuario = new ConcurrentHashMap<>();
    /** idUsuario → instante (ms) antes del cual sus sesiones quedan cerradas. */
    private final Map<Long, Long> cierreForzado = new ConcurrentHashMap<>();

    private final IUsuarioDao usuarioDao;
    private final IOpcionMenuService opcionMenuService;
    private final SseEmitterRegistry sse;
    private final SesionControlService sesionControl;

    public SesionPermisosService(IUsuarioDao usuarioDao, IOpcionMenuService opcionMenuService,
            SseEmitterRegistry sse, SesionControlService sesionControl) {
        this.usuarioDao = usuarioDao;
        this.opcionMenuService = opcionMenuService;
        this.sse = sse;
        this.sesionControl = sesionControl;
    }

    // ── Avisos de cambio ───────────────────────────────────────────────────

    /**
     * Cambiaron los permisos, el rol o los datos de un usuario: sus sesiones se
     * recalculan en la próxima petición y el navegador se entera ya.
     */
    public void usuarioCambio(Long idUsuario, String mensaje) {
        if (idUsuario == null) return;
        alConfirmar(() -> {
            versionPorUsuario.merge(idUsuario, 1L, Long::sum);
            sse.enviarAUsuario(idUsuario, EVENTO_PERMISOS, payload(mensaje, false));
        });
    }

    /**
     * Cambiaron los datos personales (nombre, C.I.) de una persona: las sesiones de sus
     * usuarios se rearman en la próxima petición (la sesión guarda la Persona) y el
     * navegador actualiza el nombre de la barra superior sin recargar (evento "perfil",
     * topbar.js). No es un cambio de permisos: no pasa por sciaf-menu-vivo.
     */
    public void personaCambio(Long idPersona, String nombreCompleto) {
        if (idPersona == null) return;
        List<Long> usuarios = usuarioDao.idsPorPersona(idPersona);
        if (usuarios.isEmpty()) return;
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("nombre", nombreCompleto);
        alConfirmar(() -> usuarios.forEach(id -> {
            versionPorUsuario.merge(id, 1L, Long::sum);
            sse.enviarAUsuario(id, EVENTO_PERFIL, datos);
        }));
    }

    /** Cambió el catálogo de menú (orden, nombre, ícono, bloqueo...): afecta a todos. */
    public void menuCambio(String mensaje) {
        alConfirmar(() -> {
            opcionMenuService.limpiarCacheMenu();
            versionGlobal.incrementAndGet();
            sse.broadcast(EVENTO_MENU, payload(mensaje, false));
        });
    }

    /**
     * Si hay una transacción en curso, espera a que se confirme. Subir la versión antes
     * del commit dejaría una ventana en la que otra petición relee los permisos VIEJOS y
     * los guarda con la versión NUEVA: esa sesión ya no se volvería a actualizar. Y el
     * navegador pediría el menú antes de que el cambio exista en la base.
     */
    private void alConfirmar(Runnable r) {
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

    /**
     * Cierra todas las sesiones abiertas del usuario (lo desactivaron, le cambiaron la
     * contraseña...). La próxima petición de cada una la invalida, y el navegador recibe
     * el aviso para volver al inicio de sesión.
     *
     * @param sesionQueSigue la sesión del que hace el cambio, si es el mismo usuario y debe
     *        seguir abierta (cambiar la propia contraseña cierra las OTRAS sesiones).
     */
    public void forzarCierre(Long idUsuario, String mensaje, HttpSession sesionQueSigue) {
        forzarCierre(idUsuario, mensaje, sesionQueSigue, "CIERRE_FORZADO", null);
    }

    /**
     * @param motivo  queda en sesion_usuario.motivo_cierre.
     * @param actor   quién las cierra (null: el sistema o el administrador sin registrar).
     * @return cuántas sesiones registradas se cerraron (sin contar las anteriores a este control).
     */
    public int forzarCierre(Long idUsuario, String mensaje, HttpSession sesionQueSigue, String motivo, Long actor) {
        if (idUsuario == null) return 0;
        long ahora = System.currentTimeMillis();
        // La sesión que sigue se marca ya (es la de esta misma petición).
        Long idQueSigue = null;
        if (sesionQueSigue != null) {
            sesionQueSigue.setAttribute(ATTR_RENOVADA, ahora + 1);
            if (sesionQueSigue.getAttribute(SesionControlService.ATTR_ID) instanceof Long id) idQueSigue = id;
        }
        // También en la base: si no, un equipo con "mantener la sesión iniciada" volvería a
        // entrar solo con su cookie apenas se le corta la sesión.
        int cerradas = sesionControl.cerrarTodas(idUsuario, idQueSigue, motivo, actor);
        alConfirmar(() -> {
            cierreForzado.put(idUsuario, ahora);
            versionPorUsuario.merge(idUsuario, 1L, Long::sum);
            // La pestaña de la sesión que sigue abierta también recibe el evento: el navegador
            // pregunta antes si su sesión sigue viva y solo sale si no (ver sciaf-menu-vivo.js).
            sse.enviarAUsuario(idUsuario, EVENTO_PERMISOS, payload(mensaje, true));
        });
        return cerradas;
    }

    private Map<String, Object> payload(String mensaje, boolean cerrarSesion) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("mensaje", mensaje);
        p.put("cerrarSesion", cerrarSesion);
        p.put("version", versionGlobal.get());
        return p;
    }

    // ── Sesión ─────────────────────────────────────────────────────────────

    private String versionDe(Long idUsuario) {
        return versionGlobal.get() + ":" + versionPorUsuario.getOrDefault(idUsuario, 0L);
    }

    /** Llena la sesión recién iniciada (lo usa el login). */
    public void iniciar(HttpSession session, Usuario usuario) {
        cargarEnSesion(session, usuario, versionDe(usuario.getIdUsuario()));
        session.setAttribute(ATTR_RENOVADA, System.currentTimeMillis());
    }

    /**
     * @param version la versión leída ANTES de consultar la base: si mientras tanto llega
     *        otro cambio, la sesión queda con la versión vieja y se vuelve a actualizar.
     */
    private void cargarEnSesion(HttpSession session, Usuario usuario, String version) {
        String rol = (usuario.getRol() != null && usuario.getRol().getNombre() != null)
                ? usuario.getRol().getNombre().toUpperCase()
                : "";
        Set<String> opciones = opcionMenuService.opcionesEfectivas(usuario);
        session.setAttribute("usuario", usuario);
        session.setAttribute("persona", usuario.getPersona());
        session.setAttribute("nombre_rol", rol);
        session.setAttribute("opciones", opciones);
        session.setAttribute(ATTR_VERSION, version);
    }

    /** Resultado de revisar una sesión en una petición. */
    public enum Revision { AL_DIA, ACTUALIZADA, CERRADA }

    /**
     * Si la sesión quedó vieja, la vuelve a armar desde la base. Devuelve CERRADA si el
     * usuario ya no puede entrar (desactivado, eliminado o con cierre forzado): el que
     * llama invalida la sesión.
     *
     * <p>Sin {@code @Transactional} a propósito: corre en CADA petición y casi siempre
     * termina en la comparación de versiones, sin tocar la base. Las consultas del camino
     * lento ya son transaccionales por sí mismas (repositorios de Spring Data).
     */
    public Revision revisar(HttpSession session) {
        Object attr = session.getAttribute("usuario");
        if (!(attr instanceof Usuario enSesion) || enSesion.getIdUsuario() == null) {
            return Revision.AL_DIA; // sin login (o login antiguo sin usuario): no es asunto nuestro
        }
        Long id = enSesion.getIdUsuario();

        Long cierre = cierreForzado.get(id);
        if (cierre != null) {
            Object renovada = session.getAttribute(ATTR_RENOVADA);
            long desde = Math.max(session.getCreationTime(),
                    renovada instanceof Long l ? l : 0L);
            if (desde < cierre) {
                return Revision.CERRADA;
            }
        }

        String version = versionDe(id);
        if (version.equals(session.getAttribute(ATTR_VERSION))) {
            return Revision.AL_DIA;
        }

        Usuario fresco = usuarioDao.findByIdConPersonaYRol(id).orElse(null);
        if (fresco == null || !"ACTIVO".equals(fresco.getEstado())) {
            return Revision.CERRADA;
        }
        cargarEnSesion(session, fresco, version);
        log.debug("Permisos de sesión actualizados para el usuario {}", id);
        return Revision.ACTUALIZADA;
    }
}
