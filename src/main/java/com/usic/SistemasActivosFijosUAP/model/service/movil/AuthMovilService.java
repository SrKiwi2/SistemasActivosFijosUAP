package com.usic.SistemasActivosFijosUAP.model.service.movil;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.config.movil.JwtService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.dao.IDispositivoMovilDao;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.LoginMovilRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.SesionMovilResponse;
import com.usic.SistemasActivosFijosUAP.model.dto.movil.UsuarioMovilDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.DispositivoMovil;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Autenticación de la app móvil.
 *
 * <p>Reutiliza las <b>mismas credenciales</b> del sistema web: mismo
 * {@code usuario}, mismo hash BCrypt y las mismas reglas de estado que
 * {@code LoginController}. No hay usuarios ni contraseñas aparte.
 *
 * <p>Modelo de sesión: access token JWT de vida corta + refresh token opaco sin
 * caducidad guardado en {@code dispositivo_movil}. Eso da el comportamiento
 * pedido —"logueado siempre hasta cerrar sesión"— sin renunciar a poder revocar
 * un teléfono perdido.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthMovilService {

    private final IUsuarioService      usuarioService;
    private final IOpcionMenuService   opcionMenuService;
    private final IDispositivoMovilDao dispositivoDao;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;

    @Value("${movil.roles-permitidos}")
    private String rolesPermitidosRaw;

    /** Excepción de negocio: el controlador la traduce a un código HTTP + JSON. */
    public static class AuthMovilException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final String codigo;

        public AuthMovilException(String codigo, String mensaje) {
            super(mensaje);
            this.codigo = codigo;
        }

        public String getCodigo() {
            return codigo;
        }
    }

    // =========================================================================
    //  LOGIN
    // =========================================================================

    @Transactional
    public SesionMovilResponse login(LoginMovilRequest req) {

        Usuario usuario = usuarioService.buscarUsuarioPorNombre(req.usuario());

        // Mismo mensaje para usuario inexistente y contraseña incorrecta: no se
        // le regala a un atacante la información de qué usuarios existen.
        if (usuario == null || !passwordEncoder.matches(req.contrasena(), usuario.getPassword())) {
            throw new AuthMovilException("CREDENCIALES", "Usuario o contraseña incorrectos");
        }

        if ("INACTIVO".equalsIgnoreCase(usuario.getEstado())
                || "ELIMINADO".equalsIgnoreCase(usuario.getEstado())) {
            throw new AuthMovilException("USUARIO_INACTIVO", "Este usuario está inactivo");
        }

        String rol = nombreRol(usuario);
        if (!rolAutorizado(rol)) {
            throw new AuthMovilException("ROL_NO_AUTORIZADO",
                    "Su rol (" + rol + ") no tiene acceso a la aplicación móvil");
        }

        Set<String> permisos = opcionMenuService.opcionesEfectivas(usuario);

        // Además del rol, se exige el permiso de acceso a la app. Así se le puede
        // quitar la app a una persona concreta sin cambiarle el rol en la web.
        if (!esAdministrador(rol) && !permisos.contains(PermisosMovil.ACCESO)) {
            throw new AuthMovilException("SIN_ACCESO_MOVIL",
                    "Su usuario no tiene habilitado el acceso a la aplicación móvil");
        }

        DispositivoMovil dispositivo = registrarDispositivo(usuario, req);

        log.info("[MOVIL] Login OK: usuario={} rol={} device={} v={}",
                usuario.getUsuario(), rol, req.deviceId(), req.appVersion());

        return new SesionMovilResponse(
                jwtService.generarAccessToken(usuario, permisos),
                dispositivo.getRefreshToken(),
                jwtService.vidaAccessTokenSegundos(),
                perfil(usuario, permisos));
    }

    /**
     * Crea o reutiliza la fila del dispositivo y le asigna un refresh token nuevo.
     * Reutilizar por (usuario, deviceId) evita acumular una fila por cada login
     * del mismo teléfono.
     */
    private DispositivoMovil registrarDispositivo(Usuario usuario, LoginMovilRequest req) {

        String deviceId = (req.deviceId() != null && !req.deviceId().isBlank())
                ? req.deviceId().trim()
                : UUID.randomUUID().toString();

        DispositivoMovil dispositivo = dispositivoDao
                .findByUsuarioIdUsuarioAndDeviceId(usuario.getIdUsuario(), deviceId)
                .orElseGet(() -> {
                    DispositivoMovil nuevo = new DispositivoMovil();
                    nuevo.setUsuario(usuario);
                    nuevo.setDeviceId(deviceId);
                    nuevo.setFechaAlta(LocalDateTime.now());
                    nuevo.setEstado("ACTIVO");
                    return nuevo;
                });

        dispositivo.setPlataforma(req.plataforma());
        dispositivo.setModelo(req.modelo());
        dispositivo.setAppVersion(req.appVersion());
        dispositivo.setRefreshToken(UUID.randomUUID().toString());
        dispositivo.setUltimoAcceso(LocalDateTime.now());
        dispositivo.setActivo(true);

        return dispositivoDao.save(dispositivo);
    }

    // =========================================================================
    //  REFRESH
    // =========================================================================

    /**
     * Renueva la sesión rotando el refresh token: el que llega queda inservible
     * en cuanto se entrega el nuevo. Si alguien roba un refresh token usado, ya
     * no le sirve.
     */
    @Transactional
    public SesionMovilResponse refresh(String refreshToken) {

        DispositivoMovil dispositivo = dispositivoDao
                .findByRefreshTokenAndActivoTrue(refreshToken)
                .orElseThrow(() -> new AuthMovilException(
                        "REFRESH_INVALIDO", "La sesión fue cerrada o revocada. Inicie sesión de nuevo"));

        Usuario usuario = dispositivo.getUsuario();

        if (usuario == null
                || "INACTIVO".equalsIgnoreCase(usuario.getEstado())
                || "ELIMINADO".equalsIgnoreCase(usuario.getEstado())) {
            dispositivo.setActivo(false);
            dispositivoDao.save(dispositivo);
            throw new AuthMovilException("USUARIO_INACTIVO", "Este usuario está inactivo");
        }

        // El rol pudo cambiar desde el último login: se revalida.
        if (!rolAutorizado(nombreRol(usuario))) {
            dispositivo.setActivo(false);
            dispositivoDao.save(dispositivo);
            throw new AuthMovilException("ROL_NO_AUTORIZADO",
                    "Su rol ya no tiene acceso a la aplicación móvil");
        }

        Set<String> permisos = opcionMenuService.opcionesEfectivas(usuario);

        dispositivo.setRefreshToken(UUID.randomUUID().toString());
        dispositivo.setUltimoAcceso(LocalDateTime.now());
        dispositivoDao.save(dispositivo);

        return new SesionMovilResponse(
                jwtService.generarAccessToken(usuario, permisos),
                dispositivo.getRefreshToken(),
                jwtService.vidaAccessTokenSegundos(),
                perfil(usuario, permisos));
    }

    // =========================================================================
    //  LOGOUT
    // =========================================================================

    /** Cierra la sesión del dispositivo (el refresh token deja de valer). */
    @Transactional
    public void logout(String refreshToken) {
        dispositivoDao.findByRefreshTokenAndActivoTrue(refreshToken)
                .ifPresent(d -> {
                    d.setActivo(false);
                    d.setRefreshToken(null);
                    dispositivoDao.save(d);
                    log.info("[MOVIL] Logout: usuario={} device={}",
                            d.getUsuario() != null ? d.getUsuario().getUsuario() : "?",
                            d.getDeviceId());
                });
    }

    // =========================================================================
    //  PERFIL
    // =========================================================================

    /** Perfil y permisos vigentes — la app lo pide en cada arranque. */
    @Transactional(readOnly = true)
    public UsuarioMovilDTO perfilDe(Long idUsuario) {
        Usuario usuario = Optional.ofNullable(usuarioService.findById(idUsuario))
                .orElseThrow(() -> new AuthMovilException("NO_ENCONTRADO", "Usuario no encontrado"));
        return perfil(usuario, opcionMenuService.opcionesEfectivas(usuario));
    }

    private UsuarioMovilDTO perfil(Usuario usuario, Set<String> permisos) {
        String nombre = (usuario.getPersona() != null)
                ? usuario.getPersona().getNombreCompleto()
                : usuario.getUsuario();

        return new UsuarioMovilDTO(
                usuario.getIdUsuario(),
                usuario.getUsuario(),
                nombre,
                nombreRol(usuario),
                permisos);
    }

    // =========================================================================
    //  Auxiliares
    // =========================================================================

    private String nombreRol(Usuario usuario) {
        return (usuario.getRol() != null && usuario.getRol().getNombre() != null)
                ? usuario.getRol().getNombre().trim().toUpperCase()
                : "";
    }

    /** ADMINISTRADOR y SUPER USUARIO nunca se quedan fuera de su propia app. */
    private boolean esAdministrador(String rol) {
        return "ADMINISTRADOR".equals(rol) || "SUPER USUARIO".equals(rol);
    }

    /** Roles con acceso a la app, configurables en {@code movil.roles-permitidos}. */
    private boolean rolAutorizado(String rol) {
        List<String> permitidos = Arrays.stream(rolesPermitidosRaw.split(","))
                .map(s -> s.trim().toUpperCase())
                .filter(s -> !s.isEmpty())
                .toList();
        return permitidos.contains(rol);
    }
}
