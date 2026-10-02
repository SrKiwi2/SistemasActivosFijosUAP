package com.usic.SistemasActivosFijosUAP.model.service.seguridad;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IRolService;
import com.usic.SistemasActivosFijosUAP.model.IService.LogAccesoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.usuario.UsuarioFilaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialPermisoUsuario;
import com.usic.SistemasActivosFijosUAP.model.entity.OpcionMenu;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.SesionUsuario;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;

import jakarta.servlet.http.HttpSession;

/**
 * Reglas de la gestión de usuarios. Como casi todo /administracion/** es permitAll, las
 * reglas de quién puede qué viven acá y no en la URL:
 *
 * <ul>
 *   <li>Solo un ADMINISTRADOR puede crear, tocar o dar el rol ADMINISTRADOR.</li>
 *   <li>Nadie se puede desactivar, eliminar ni quitar el rol a sí mismo.</li>
 *   <li>No se puede dejar el sistema sin ningún ADMINISTRADOR activo.</li>
 *   <li>Contraseñas: al menos 8 caracteres, con letras y números.</li>
 * </ul>
 *
 * Todo cambio queda en el monitoreo de actividad, y se aplica en vivo a las sesiones
 * abiertas del usuario afectado (permisos nuevos, o cierre de sesión si se lo desactivó
 * o se le cambió la contraseña).
 */
@Service
public class GestionUsuariosService {

    private static final Logger log = LoggerFactory.getLogger(GestionUsuariosService.class);

    public static final String ACTIVO = "ACTIVO";
    public static final String INACTIVO = "INACTIVO";
    public static final String ELIMINADO = "ELIMINADO";

    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String DIGITOS = "23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IUsuarioDao usuarioDao;
    private final IPersonaService personaService;
    private final IRolService rolService;
    private final IOpcionMenuService opcionMenuService;
    private final PasswordEncoder passwordEncoder;
    private final SesionPermisosService sesionPermisos;
    private final ActividadService actividad;
    private final LogAccesoService logAcceso;
    private final SseEmitterRegistry sse;
    private final AuditoriaPermisosService auditoria;
    private final SesionControlService sesionControl;

    public GestionUsuariosService(IUsuarioDao usuarioDao, IPersonaService personaService, IRolService rolService,
            IOpcionMenuService opcionMenuService, PasswordEncoder passwordEncoder,
            SesionPermisosService sesionPermisos, ActividadService actividad, LogAccesoService logAcceso,
            SseEmitterRegistry sse, AuditoriaPermisosService auditoria, SesionControlService sesionControl) {
        this.auditoria = auditoria;
        this.sesionControl = sesionControl;
        this.usuarioDao = usuarioDao;
        this.personaService = personaService;
        this.rolService = rolService;
        this.opcionMenuService = opcionMenuService;
        this.passwordEncoder = passwordEncoder;
        this.sesionPermisos = sesionPermisos;
        this.actividad = actividad;
        this.logAcceso = logAcceso;
        this.sse = sse;
    }

    // ── Consulta ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UsuarioFilaDto> listar(Usuario actor) {
        Map<String, LocalDateTime> ultimos = Map.of();
        Map<String, Long> fallidos = Map.of();
        try {
            ultimos = logAcceso.ultimoIngresoPorUsuario();
            fallidos = logAcceso.fallidosRecientesPorUsuario(7);
        } catch (Exception e) {
            // Sin log_acceso la pantalla igual tiene que abrir.
            log.warn("No se pudo leer log_acceso: {}", e.getMessage());
        }
        Map<Long, Integer> permisos = new HashMap<>();
        for (Object[] f : usuarioDao.contarPermisosPorUsuario()) {
            permisos.put(((Number) f[0]).longValue(), ((Number) f[1]).intValue());
        }

        List<Usuario> usuarios = usuarioDao.listarParaGestion();
        // Respaldo de permisos de los inactivos (lo que se restauraría al activarlos).
        Map<Long, Integer> respaldos = Map.of();
        try {
            respaldos = auditoria.cantidadesRespaldo(usuarios.stream()
                    .filter(u -> !ACTIVO.equals(u.getEstado())).map(Usuario::getIdUsuario).toList());
        } catch (Exception e) {
            log.warn("No se pudo leer historial_permiso_usuario (¿falta el script?): {}", e.getMessage());
        }

        List<UsuarioFilaDto> filas = new ArrayList<>();
        for (Usuario u : usuarios) {
            UsuarioFilaDto f = new UsuarioFilaDto();
            f.setIdUsuario(u.getIdUsuario());
            try {
                f.setIdCifrado(Encriptar.encrypt(Long.toString(u.getIdUsuario())));
            } catch (Exception e) {
                f.setIdCifrado(null);
            }
            f.setUsuario(u.getUsuario());
            Persona p = u.getPersona();
            f.setNombreCompleto(p != null ? p.getNombreCompleto() : "(sin persona)");
            f.setCi(p != null ? p.getCi() : null);
            f.setRol(u.getRol() != null ? u.getRol().getNombre() : "(sin rol)");
            f.setEstado(u.getEstado() != null ? u.getEstado() : ACTIVO);
            f.setConectado(sse.isUsuarioConectado(u.getIdUsuario()));
            String clave = u.getUsuario() != null ? u.getUsuario().toLowerCase() : "";
            f.setUltimoIngreso(ultimos.get(clave));
            f.setFallidosRecientes(fallidos.getOrDefault(clave, 0L));
            f.setPermisosPropios(permisos.getOrDefault(u.getIdUsuario(), 0));
            f.setRegistro(u.getRegistro());
            f.setEsYo(actor != null && u.getIdUsuario().equals(actor.getIdUsuario()));
            f.setRespaldo(respaldos.get(u.getIdUsuario()));
            filas.add(f);
        }
        // Primero los conectados, después los activos; dentro, por nombre.
        filas.sort(Comparator.comparing((UsuarioFilaDto f) -> !f.isConectado())
                .thenComparing(f -> !ACTIVO.equals(f.getEstado()))
                .thenComparing(f -> f.getNombreCompleto() == null ? "" : f.getNombreCompleto()));
        return filas;
    }

    public List<Map<String, Object>> accesos(Long idUsuario, int limite) {
        Usuario u = obtener(idUsuario);
        List<Map<String, Object>> lista = new ArrayList<>();
        logAcceso.ultimosDe(u.getUsuario(), limite).forEach(l -> {
            Map<String, Object> m = new HashMap<>();
            m.put("fecha", l.getFechaHora() != null ? l.getFechaHora().toString() : null);
            m.put("exito", l.getExito());
            m.put("motivo", l.getMotivo());
            m.put("ip", l.getIp());
            m.put("navegador", resumirNavegador(l.getUserAgent()));
            lista.add(m);
        });
        return lista;
    }

    // ── Alta y modificación ────────────────────────────────────────────────

    @Transactional
    public Usuario registrar(Usuario actor, String nombreUsuario, String password, String confirmacion,
            Long idPersona, Long idRol) {
        String nombre = normalizarNombre(nombreUsuario);
        validarContrasena(password, confirmacion);
        if (usuarioDao.existeNombre(nombre, null)) {
            throw new ReglaNegocioException("Ya existe un usuario «" + nombre + "» (puede estar inactivo). Elija otro nombre.");
        }
        Persona persona = persona(idPersona);
        if (usuarioDao.personaTieneUsuario(persona.getIdPersona(), null)) {
            throw new ReglaNegocioException(persona.getNombreCompleto() + " ya tiene un usuario. Edítelo en vez de crear otro.");
        }
        Rol rol = rol(idRol);
        exigirPuedeAsignarRol(actor, rol);

        Usuario u = new Usuario();
        u.setUsuario(nombre);
        u.setPassword(passwordEncoder.encode(password));
        u.setEstado(ACTIVO);
        u.setPersona(persona);
        u.setRol(rol);
        Usuario guardado = usuarioDao.save(u);
        registrarActividad(actor, ActividadService.ACC_REGISTRO, guardado,
                "Creó el usuario «" + nombre + "» (" + rol.getNombre() + ") para " + persona.getNombreCompleto());
        return guardado;
    }

    @Transactional
    public Usuario modificar(Usuario actor, Long idUsuario, String nombreUsuario, Long idPersona, Long idRol) {
        Usuario u = obtener(idUsuario);
        exigirPuedeTocar(actor, u);
        String nombre = normalizarNombre(nombreUsuario);
        if (usuarioDao.existeNombre(nombre, u.getIdUsuario())) {
            throw new ReglaNegocioException("Ya existe otro usuario «" + nombre + "».");
        }
        Persona persona = persona(idPersona);
        if (usuarioDao.personaTieneUsuario(persona.getIdPersona(), u.getIdUsuario())) {
            throw new ReglaNegocioException(persona.getNombreCompleto() + " ya tiene otro usuario.");
        }
        Rol rol = rol(idRol);
        exigirPuedeAsignarRol(actor, rol);

        String rolAntes = RolesSciaf.rolDe(u);
        boolean cambiaRol = !rolAntes.equalsIgnoreCase(rol.getNombre());
        if (cambiaRol && esYo(actor, u)) {
            throw new ReglaNegocioException("No puede cambiarse el rol a sí mismo: pídaselo a otro administrador.");
        }
        if (cambiaRol && RolesSciaf.ADMINISTRADOR.equals(rolAntes)) {
            exigirOtroAdministrador(u);
        }
        String nombreAntes = u.getUsuario();

        u.setUsuario(nombre);
        u.setPersona(persona);
        u.setRol(rol);
        Usuario guardado = usuarioDao.save(u);

        StringBuilder d = new StringBuilder("Modificó el usuario «" + nombre + "»");
        if (!nombre.equals(nombreAntes)) d.append(" (antes «").append(nombreAntes).append("»)");
        if (cambiaRol) d.append(": rol ").append(rolAntes).append(" → ").append(rol.getNombre());
        registrarActividad(actor, ActividadService.ACC_MODIFICACION, guardado, d.toString());
        if (cambiaRol) {
            // Sus permisos propios no cambian, pero si usa la plantilla, su menú sí.
            Set<String> propios = codigosPropios(guardado);
            auditoria.registrar(guardado, HistorialPermisoUsuario.CAMBIO_ROL, "Usuarios › Editar", propios, propios, null,
                    "Rol " + rolAntes + " → " + rol.getNombre()
                            + (propios.isEmpty() ? ". Usa la plantilla del rol: su menú pasa a ser el del rol nuevo" : ""));
        }
        if (cambiaRol) {
            sesionPermisos.usuarioCambio(u.getIdUsuario(), "Su rol cambió a " + rol.getNombre());
        } else {
            sesionPermisos.usuarioCambio(u.getIdUsuario(), "Sus datos de usuario fueron actualizados");
        }
        return guardado;
    }

    // ── Contraseña ─────────────────────────────────────────────────────────

    /**
     * El administrador fija una contraseña nueva (o el sistema genera una). Devuelve la
     * contraseña en claro para mostrarla UNA vez y que se la entregue al usuario.
     */
    @Transactional
    public String restablecerContrasena(Usuario actor, Long idUsuario, String nueva, boolean cerrarSesiones) {
        Usuario u = obtener(idUsuario);
        exigirPuedeTocar(actor, u);
        String clave = (nueva == null || nueva.isBlank()) ? generarContrasena() : nueva;
        validarContrasena(clave, clave);
        u.setPassword(passwordEncoder.encode(clave));
        usuarioDao.save(u);
        registrarActividad(actor, ActividadService.ACC_MODIFICACION, u,
                "Restableció la contraseña de «" + u.getUsuario() + "»" + (cerrarSesiones ? " y cerró sus sesiones" : ""));
        if (cerrarSesiones && !esYo(actor, u)) {
            sesionPermisos.forzarCierre(u.getIdUsuario(), "El administrador cambió su contraseña. Ingrese con la nueva.", null);
        } else if (!esYo(actor, u)) {
            // Aunque no se cierren las sesiones abiertas, los equipos con la sesión mantenida
            // no pueden seguir entrando con la contraseña vieja.
            sesionControl.cerrarRecordadas(u.getIdUsuario(), null, "CAMBIO_CONTRASENA", actor.getIdUsuario());
        }
        return clave;
    }

    /** Cada usuario cambia la suya: exige la actual. */
    @Transactional
    public void cambiarMiContrasena(Usuario actor, HttpSession sesion, String actual, String nueva,
            String confirmacion, boolean cerrarOtras) {
        if (actor == null) throw new ReglaNegocioException("Su sesión expiró. Vuelva a ingresar.");
        Usuario u = obtener(actor.getIdUsuario());
        if (actual == null || !passwordEncoder.matches(actual, u.getPassword())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
        validarContrasena(nueva, confirmacion);
        if (passwordEncoder.matches(nueva, u.getPassword())) {
            throw new ReglaNegocioException("La contraseña nueva tiene que ser distinta de la actual.");
        }
        u.setPassword(passwordEncoder.encode(nueva));
        usuarioDao.save(u);
        registrarActividad(u, ActividadService.ACC_MODIFICACION, u, "Cambió su propia contraseña");
        if (cerrarOtras) {
            sesionPermisos.forzarCierre(u.getIdUsuario(),
                    "Su contraseña se cambió desde otra sesión. Ingrese con la nueva.", sesion);
        } else {
            // Las otras sesiones abiertas siguen, pero los equipos con la sesión mantenida
            // no pueden seguir entrando con la contraseña vieja.
            Long idActual = sesion != null && sesion.getAttribute(SesionControlService.ATTR_ID) instanceof Long id ? id : null;
            sesionControl.cerrarRecordadas(u.getIdUsuario(), idActual, "CAMBIO_CONTRASENA", u.getIdUsuario());
        }
    }

    public String generarContrasena() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        for (int i = 0; i < 3; i++) sb.append(DIGITOS.charAt(RANDOM.nextInt(DIGITOS.length())));
        // Mezclar para que los números no queden siempre al final.
        char[] c = sb.toString().toCharArray();
        for (int i = c.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char t = c[i]; c[i] = c[j]; c[j] = t;
        }
        return new String(c);
    }

    // ── Estado y sesiones ──────────────────────────────────────────────────

    /**
     * Desactivar: se respaldan y se quitan sus permisos (ver {@link #dejarSinAcceso}).
     * Activar: si {@code restaurar}, vuelve a tener los permisos del último respaldo (los
     * que sigan existiendo en el menú); si no, queda con la plantilla de su rol.
     *
     * @return mensaje para mostrar
     */
    @Transactional
    public String cambiarEstado(Usuario actor, Long idUsuario, boolean activar, boolean restaurar) {
        Usuario u = obtener(idUsuario);
        exigirPuedeTocar(actor, u);
        if (!activar) {
            if (esYo(actor, u)) throw new ReglaNegocioException("No puede desactivarse a sí mismo.");
            dejarSinAcceso(actor, u, HistorialPermisoUsuario.DESACTIVAR, ORIGEN_ESTADO, "Usuario desactivado");
            return "Usuario desactivado: no puede ingresar, sus sesiones se cerraron y se guardó el respaldo de sus permisos";
        }

        Set<String> antes = codigosPropios(u);
        Set<String> restaurados = new TreeSet<>();
        String detalle;
        Optional<Set<String>> respaldo = auditoria.ultimoRespaldo(u.getIdUsuario());
        if (restaurar && respaldo.isPresent() && !respaldo.get().isEmpty()) {
            List<OpcionMenu> opciones = opcionMenuService.buscarPorCodigos(respaldo.get());
            opciones.forEach(o -> restaurados.add(o.getCodigo()));
            u.setOpciones(new HashSet<>(opciones));
            int perdidos = respaldo.get().size() - opciones.size();
            detalle = "Activado con " + opciones.size() + " permiso(s) restaurado(s) del respaldo"
                    + (perdidos > 0 ? " (" + perdidos + " ya no existen en el menú)" : "");
        } else {
            u.getOpciones().clear();
            detalle = restaurar ? "Activado con la plantilla de su rol (no tenía permisos propios en el respaldo)"
                                : "Activado sin restaurar el respaldo: usa la plantilla de su rol";
        }
        u.setEstado(ACTIVO);
        usuarioDao.save(u);
        auditoria.registrar(u, HistorialPermisoUsuario.ACTIVAR, ORIGEN_ESTADO, antes, restaurados, null, detalle);
        registrarActividad(actor, ActividadService.ACC_DESBLOQUEO, u, "Activó a «" + u.getUsuario() + "». " + detalle);
        sesionPermisos.usuarioCambio(u.getIdUsuario(), "Su usuario fue activado");
        return "Usuario activado. " + detalle;
    }

    public void cerrarSesiones(Usuario actor, Long idUsuario) {
        Usuario u = obtener(idUsuario);
        exigirPuedeTocar(actor, u);
        if (esYo(actor, u)) throw new ReglaNegocioException("Para cerrar su propia sesión use «Cerrar sesión».");
        sesionPermisos.forzarCierre(u.getIdUsuario(), "El administrador cerró su sesión.", null);
        registrarActividad(actor, ActividadService.ACC_BLOQUEO, u, "Cerró las sesiones abiertas de «" + u.getUsuario() + "»");
    }

    /** Sesiones abiertas de un usuario, equipo por equipo (Usuarios → Sesiones abiertas). */
    public List<Map<String, Object>> sesionesDe(Usuario actor, Long idUsuario) {
        Usuario u = obtener(idUsuario);
        exigirPuedeTocar(actor, u); // las de un ADMINISTRADOR solo las ve otro ADMINISTRADOR
        return sesionControl.abiertas(u.getIdUsuario(), null);
    }

    /** Cierra la sesión de UN equipo de otro usuario. */
    public void cerrarSesionDe(Usuario actor, Long idUsuario, Long idSesion) {
        Usuario u = obtener(idUsuario);
        exigirPuedeTocar(actor, u);
        if (esYo(actor, u)) throw new ReglaNegocioException("Sus propias sesiones se manejan en «Mis sesiones abiertas».");
        SesionUsuario s = sesionControl.obtener(idSesion);
        if (s == null || !u.getIdUsuario().equals(s.getUsuario().getIdUsuario())
                || !SesionUsuario.ACTIVA.equals(s.getEstado())) {
            throw new ReglaNegocioException("Esa sesión ya no está abierta.");
        }
        sesionControl.cerrarADistancia(idSesion, "CERRADA_POR_ADMIN", actor.getIdUsuario());
        registrarActividad(actor, ActividadService.ACC_BLOQUEO, u, "Cerró la sesión de «" + u.getUsuario()
                + "» en " + s.getDispositivo() + (s.getIp() != null ? " (IP " + s.getIp() + ")" : ""));
    }

    @Transactional
    public void eliminar(Usuario actor, Long idUsuario) {
        Usuario u = obtener(idUsuario);
        exigirPuedeTocar(actor, u);
        if (esYo(actor, u)) throw new ReglaNegocioException("No puede eliminarse a sí mismo.");
        if (RolesSciaf.esAdministrador(u)) exigirOtroAdministrador(u);
        Set<String> antes = codigosPropios(u);
        u.setEstado(ELIMINADO);
        usuarioDao.save(u);
        auditoria.registrar(u, HistorialPermisoUsuario.ELIMINAR, "Usuarios › Eliminar", antes, antes, antes,
                "Usuario eliminado. Tenía " + (antes.isEmpty() ? "la plantilla de su rol" : antes.size() + " permiso(s) propios"));
        registrarActividad(actor, ActividadService.ACC_ELIMINACION, u, "Eliminó al usuario «" + u.getUsuario() + "»");
        sesionPermisos.forzarCierre(u.getIdUsuario(), "Su usuario fue dado de baja.", null);
    }

    // ── Permisos ───────────────────────────────────────────────────────────

    public static final String ORIGEN_PERMISOS = "Usuarios › Permisos de menú";
    public static final String ORIGEN_ESTADO = "Usuarios › Activar / desactivar";

    /**
     * Guarda los permisos de menú. Tres casos:
     * <ul>
     *   <li>{@code usarPlantilla}: se borran los permisos propios y vuelve a ver lo que define su rol;</li>
     *   <li>sin ninguna opción marcada: queda <b>sin acceso</b> — se lo desactiva, se cierran sus
     *       sesiones y se guarda el respaldo de lo que tenía (se restaura al activarlo);</li>
     *   <li>con opciones: esas son sus permisos.</li>
     * </ul>
     * Antes, "quitar todos" guardaba la lista vacía y eso significaba "usar la plantilla del
     * rol": el usuario seguía viendo el mismo menú y no quedaba rastro.
     *
     * @return mensaje para mostrar
     */
    @Transactional
    public String guardarPermisos(Usuario actor, Long idUsuario, List<String> codigos, boolean usarPlantilla) {
        Usuario u = obtener(idUsuario);
        exigirPuedeTocar(actor, u);
        if (!ACTIVO.equals(u.getEstado())) {
            // Sus permisos están en el respaldo: si se tocaran acá, activarlo los pisaría.
            throw new ReglaNegocioException("«" + u.getUsuario() + "» está desactivado. Actívelo primero "
                    + "(puede restaurar su respaldo) y después ajuste sus permisos.");
        }
        Set<String> antes = codigosPropios(u);

        if (usarPlantilla) {
            u.getOpciones().clear();
            usuarioDao.save(u);
            auditoria.registrar(u, HistorialPermisoUsuario.PLANTILLA_ROL, ORIGEN_PERMISOS, antes, Set.of(), null,
                    "Vuelve a la plantilla del rol " + RolesSciaf.rolDe(u));
            registrarActividad(actor, ActividadService.ACC_MODIFICACION, u,
                    "Dejó a «" + u.getUsuario() + "» con la plantilla de su rol (" + RolesSciaf.rolDe(u) + ")");
            sesionPermisos.usuarioCambio(u.getIdUsuario(), "Sus permisos ahora son los de su rol");
            return "«" + u.getUsuario() + "» vuelve a ver el menú de su rol (" + RolesSciaf.rolDe(u) + ")";
        }

        List<OpcionMenu> opciones = opcionMenuService.buscarPorCodigos(codigos);
        if (opciones.isEmpty()) {
            dejarSinAcceso(actor, u, HistorialPermisoUsuario.SIN_ACCESO, ORIGEN_PERMISOS,
                    "Se le quitaron todos los permisos de menú");
            return "Se quitaron todos sus permisos: «" + u.getUsuario() + "» quedó desactivado y sin acceso. "
                    + "Se guardó el respaldo de " + antes.size() + " permiso(s) para restaurarlos al activarlo";
        }

        Set<String> despues = opciones.stream().map(OpcionMenu::getCodigo).collect(Collectors.toCollection(TreeSet::new));
        if (despues.equals(antes)) {
            return "No hubo cambios en los permisos";
        }
        u.setOpciones(new HashSet<>(opciones));
        usuarioDao.save(u);
        HistorialPermisoUsuario h = auditoria.registrar(u, HistorialPermisoUsuario.ASIGNACION, ORIGEN_PERMISOS,
                antes, despues, null, antes.isEmpty() ? "Antes usaba la plantilla de su rol" : null);
        int mas = AuditoriaPermisosService.separar(h.getAgregados()).size();
        int menos = AuditoriaPermisosService.separar(h.getQuitados()).size();
        registrarActividad(actor, ActividadService.ACC_MODIFICACION, u,
                "Permisos de «" + u.getUsuario() + "»: +" + mas + " / −" + menos + " (" + despues.size() + " en total)");
        sesionPermisos.usuarioCambio(u.getIdUsuario(), "El administrador actualizó sus permisos");
        return "Permisos actualizados: +" + mas + " agregado(s), −" + menos + " quitado(s) (" + despues.size() + " en total)";
    }

    /**
     * Sin acceso: respalda los permisos propios, los borra, desactiva al usuario y cierra
     * sus sesiones. Lo usan "quitar todos" y "desactivar".
     */
    private void dejarSinAcceso(Usuario actor, Usuario u, String accion, String origen, String detalle) {
        if (esYo(actor, u)) throw new ReglaNegocioException("No puede dejarse sin acceso a sí mismo.");
        if (RolesSciaf.esAdministrador(u) && ACTIVO.equals(u.getEstado())) exigirOtroAdministrador(u);
        Set<String> antes = codigosPropios(u);
        u.getOpciones().clear();
        boolean estabaActivo = ACTIVO.equals(u.getEstado());
        u.setEstado(INACTIVO);
        usuarioDao.save(u);
        auditoria.registrar(u, accion, origen, antes, Set.of(), antes,
                detalle + (antes.isEmpty() ? ". Usaba la plantilla de su rol" : ". Respaldo: " + antes.size() + " permiso(s)"));
        registrarActividad(actor, ActividadService.ACC_BLOQUEO, u,
                detalle + " — «" + u.getUsuario() + "» quedó desactivado" + (estabaActivo ? "" : " (ya estaba inactivo)"));
        sesionPermisos.forzarCierre(u.getIdUsuario(), "Su usuario fue desactivado por el administrador.", null);
    }

    /** Lo que se restauraría al activarlo (para la pregunta de confirmación). */
    @Transactional(readOnly = true)
    public Map<String, Object> respaldo(Long idUsuario) {
        Usuario u = obtener(idUsuario);
        Map<String, Object> m = new HashMap<>();
        Optional<Set<String>> r = auditoria.ultimoRespaldo(u.getIdUsuario());
        m.put("hay", r.isPresent());
        m.put("cantidad", r.map(Set::size).orElse(0));
        m.put("usaPlantilla", r.map(Set::isEmpty).orElse(true));
        m.put("nombres", r.map(s -> opcionMenuService.buscarPorCodigos(s).stream()
                .map(OpcionMenu::getDescripcion).sorted().toList()).orElse(List.of()));
        m.put("rol", RolesSciaf.rolDe(u));
        return m;
    }

    private Set<String> codigosPropios(Usuario u) {
        Set<String> s = new TreeSet<>();
        if (u.getOpciones() != null) u.getOpciones().forEach(o -> s.add(o.getCodigo()));
        return s;
    }

    public boolean conectado(Long idUsuario) {
        return sse.isUsuarioConectado(idUsuario);
    }

    // ── Reglas ─────────────────────────────────────────────────────────────

    /** Quién puede entrar a la gestión de usuarios: ADMINISTRADOR o quien tenga la opción. */
    public static boolean puedeGestionar(Usuario actor, HttpSession sesion) {
        if (actor == null) return false;
        if (RolesSciaf.esAdministrador(actor)) return true;
        Object ops = sesion != null ? sesion.getAttribute("opciones") : null;
        return ops instanceof java.util.Set<?> s && s.contains("opcion_usuario");
    }

    private void exigirPuedeTocar(Usuario actor, Usuario objetivo) {
        if (RolesSciaf.esAdministrador(objetivo) && !RolesSciaf.esAdministrador(actor)) {
            throw new ReglaNegocioException("Solo un ADMINISTRADOR puede modificar a otro ADMINISTRADOR.");
        }
    }

    private void exigirPuedeAsignarRol(Usuario actor, Rol rol) {
        if (RolesSciaf.ADMINISTRADOR.equalsIgnoreCase(rol.getNombre()) && !RolesSciaf.esAdministrador(actor)) {
            throw new ReglaNegocioException("Solo un ADMINISTRADOR puede dar el rol ADMINISTRADOR.");
        }
    }

    private void exigirOtroAdministrador(Usuario u) {
        long activos = usuarioDao.contarActivosPorRol(RolesSciaf.ADMINISTRADOR);
        boolean esActivo = ACTIVO.equals(u.getEstado());
        if (activos - (esActivo ? 1 : 0) < 1) {
            throw new ReglaNegocioException("Es el único ADMINISTRADOR activo: el sistema se quedaría sin administrador.");
        }
    }

    private boolean esYo(Usuario actor, Usuario u) {
        return actor != null && u.getIdUsuario().equals(actor.getIdUsuario());
    }

    public static void validarContrasena(String password, String confirmacion) {
        if (password == null || password.length() < 8) {
            throw new ReglaNegocioException("La contraseña debe tener al menos 8 caracteres.");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new ReglaNegocioException("La contraseña debe tener letras y números.");
        }
        if (password.length() > 72) {
            throw new ReglaNegocioException("La contraseña no puede pasar de 72 caracteres.");
        }
        if (confirmacion != null && !password.equals(confirmacion)) {
            throw new ReglaNegocioException("La confirmación no coincide con la contraseña.");
        }
    }

    private String normalizarNombre(String nombre) {
        String n = nombre == null ? "" : nombre.trim();
        if (n.length() < 3 || n.length() > 40) {
            throw new ReglaNegocioException("El nombre de usuario debe tener entre 3 y 40 caracteres.");
        }
        if (!n.matches("[A-Za-z0-9._@-]+")) {
            throw new ReglaNegocioException("El nombre de usuario solo puede tener letras, números y . _ - @ (sin espacios).");
        }
        return n;
    }

    private Usuario obtener(Long idUsuario) {
        Usuario u = idUsuario != null ? usuarioDao.findByIdConPersonaYRol(idUsuario).orElse(null) : null;
        if (u == null || ELIMINADO.equals(u.getEstado())) {
            throw new ReglaNegocioException("El usuario no existe.");
        }
        return u;
    }

    private Persona persona(Long id) {
        Persona p = id != null ? personaService.findById(id) : null;
        if (p == null) throw new ReglaNegocioException("Seleccione una persona válida.");
        return p;
    }

    private Rol rol(Long id) {
        Rol r = id != null ? rolService.findById(id) : null;
        if (r == null) throw new ReglaNegocioException("Seleccione un rol válido.");
        return r;
    }

    private void registrarActividad(Usuario actor, String accion, Usuario objetivo, String descripcion) {
        actividad.registrar(actor, ActividadService.MOD_USUARIO, accion,
                objetivo != null ? objetivo.getUsuario() : null, descripcion,
                objetivo != null ? objetivo.getIdUsuario() : null);
    }

    private String resumirNavegador(String ua) {
        if (ua == null) return null;
        String so = ua.contains("Android") ? "Android" : ua.contains("iPhone") ? "iPhone"
                : ua.contains("Windows") ? "Windows" : ua.contains("Mac OS") ? "Mac" : ua.contains("Linux") ? "Linux" : "";
        String nav = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera" : ua.contains("Chrome/") ? "Chrome"
                : ua.contains("Firefox/") ? "Firefox" : ua.contains("Safari/") ? "Safari" : "Otro";
        return (nav + (so.isEmpty() ? "" : " · " + so));
    }
}
