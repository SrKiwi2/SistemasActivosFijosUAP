package com.usic.SistemasActivosFijosUAP.controller.usuario;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.IService.IOpcionMenuService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IRolService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.dto.usuario.UsuarioFilaDto;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.AuditoriaPermisosService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.GestionUsuariosService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Gestión de usuarios: alta, edición, permisos de menú, contraseña, activar/desactivar,
 * cerrar sesiones e historial de accesos. Las reglas (quién puede qué) están en
 * {@link GestionUsuariosService}; acá solo se traduce a HTTP.
 *
 * <p>Los ids viajan cifrados ({@link Encriptar}), como siempre en este módulo.
 */
@Controller
@RequestMapping("/administracion/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final IUsuarioService usuarioService;
    private final IPersonaService personaService;
    private final IRolService rolService;
    private final IOpcionMenuService opcionMenuService;
    private final GestionUsuariosService gestion;
    private final AuditoriaPermisosService auditoria;

    /** Dirección del sistema que se le entrega al usuario nuevo (la misma de las actas). */
    @Value("${sciaf.url.publica:https://sciaf.uap.edu.bo}")
    private String urlPublica;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "usuario/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model, HttpServletRequest request) {
        // Los contadores (en línea, activos, …) y la lista de roles del filtro los arma la
        // vista a partir de las filas (data-*), así siempre coinciden con lo que se ve.
        List<UsuarioFilaDto> filas = gestion.listar(RolesSciaf.usuarioDe(request));
        model.addAttribute("filas", filas);
        return "usuario/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("listaPersonas", personaService.listarPersonas());
        model.addAttribute("listaRoles", rolService.listarRoles());
        model.addAttribute("edit", false);
        return "usuario/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_usuario}")
    public String formularioEdit(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {
        Long id = descifrar(idUsuario);
        model.addAttribute("usuario", usuarioService.findById(id));
        model.addAttribute("listaPersonas", personaService.listarPersonas());
        model.addAttribute("listaRoles", rolService.listarRoles());
        model.addAttribute("edit", true);
        return "usuario/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-usuario")
    public ResponseEntity<Map<String, Object>> registrar(
            HttpServletRequest request,
            @RequestParam("usuario") String nombreUsuario,
            @RequestParam("password") String password,
            @RequestParam(value = "confirmacion", required = false) String confirmacion,
            @RequestParam("persona.idPersona") Long idPersona,
            @RequestParam("rol.idRol") Long idRol) {
        return ejecutar(request, () -> {
            Usuario nuevo = gestion.registrar(RolesSciaf.usuarioDe(request), nombreUsuario, password,
                    confirmacion != null ? confirmacion : password, idPersona, idRol);
            // Datos tal como quedaron guardados, para armar el mensaje de bienvenida que el
            // administrador copia y envía. La contraseña no se devuelve: la tiene el navegador.
            Map<String, Object> r = new HashMap<>();
            r.put("msg", "Se realizó el registro correctamente. Asígnele sus permisos con el botón «Permisos»");
            r.put("usuario", nuevo.getUsuario());
            r.put("nombre", nombreCompleto(nuevo));
            r.put("rol", nuevo.getRol() != null ? nuevo.getRol().getNombre() : "");
            r.put("enlace", urlPublica.endsWith("/") ? urlPublica : urlPublica + "/");
            return r;
        });
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-usuario")
    public ResponseEntity<Map<String, Object>> modificar(
            HttpServletRequest request,
            @RequestParam("idUsuario") Long idUsuario,
            @RequestParam("usuario") String nombreUsuario,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam("persona.idPersona") Long idPersona,
            @RequestParam("rol.idRol") Long idRol) {
        return ejecutar(request, () -> {
            Usuario actor = RolesSciaf.usuarioDe(request);
            gestion.modificar(actor, idUsuario, nombreUsuario, idPersona, idRol);
            // Compatibilidad: el formulario viejo mandaba la contraseña acá.
            if (password != null && !password.isBlank()) {
                gestion.restablecerContrasena(actor, idUsuario, password, false);
            }
            return Map.of("msg", "Se realizó la modificación correctamente. Si estaba conectado, ya lo ve aplicado");
        });
    }

    // ── Contraseña, estado y sesiones ──────────────────────────────────────

    /** Fija la contraseña que se indica, o genera una si viene vacía. Devuelve la contraseña UNA vez. */
    @ValidarUsuarioAutenticado
    @PostMapping("/restablecer-contrasena/{id_usuario}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> restablecer(HttpServletRequest request,
            @PathVariable("id_usuario") String idUsuario,
            @RequestParam(value = "nueva", required = false) String nueva,
            @RequestParam(value = "cerrarSesiones", defaultValue = "true") boolean cerrarSesiones) {
        return ejecutar(request, () -> {
            String clave = gestion.restablecerContrasena(RolesSciaf.usuarioDe(request), descifrar(idUsuario),
                    nueva, cerrarSesiones);
            Map<String, Object> r = new HashMap<>();
            r.put("msg", "Contraseña restablecida");
            r.put("contrasena", clave);
            return r;
        });
    }

    /** Activar ({@code restaurar}: con los permisos del respaldo) o desactivar (respalda y quita). */
    @ValidarUsuarioAutenticado
    @PostMapping("/estado/{id_usuario}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> estado(HttpServletRequest request,
            @PathVariable("id_usuario") String idUsuario, @RequestParam("activo") boolean activo,
            @RequestParam(value = "restaurar", defaultValue = "true") boolean restaurar) {
        return ejecutar(request, () -> Map.of("msg",
                gestion.cambiarEstado(RolesSciaf.usuarioDe(request), descifrar(idUsuario), activo, restaurar)));
    }

    /** Qué permisos se restaurarían al activarlo (para la confirmación). */
    @ValidarUsuarioAutenticado
    @GetMapping("/respaldo/{id_usuario}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> respaldo(HttpServletRequest request,
            @PathVariable("id_usuario") String idUsuario) {
        return ejecutar(request, () -> gestion.respaldo(descifrar(idUsuario)));
    }

    /** Auditoría de permisos: qué se agregó/quitó, quién, cuándo, desde dónde. */
    @ValidarUsuarioAutenticado
    @GetMapping("/historial-permisos/{id_usuario}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> historialPermisos(HttpServletRequest request,
            @PathVariable("id_usuario") String idUsuario) {
        return ejecutar(request, () -> Map.of("historial", auditoria.historial(descifrar(idUsuario), 100)));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/cerrar-sesiones/{id_usuario}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cerrarSesiones(HttpServletRequest request,
            @PathVariable("id_usuario") String idUsuario) {
        return ejecutar(request, () -> {
            gestion.cerrarSesiones(RolesSciaf.usuarioDe(request), descifrar(idUsuario));
            return Map.of("msg", "Se cerraron sus sesiones abiertas");
        });
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/sesiones/{id_usuario}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sesiones(HttpServletRequest request,
            @PathVariable("id_usuario") String idUsuario) {
        return ejecutar(request, () -> Map.of("sesiones", gestion.sesionesDe(RolesSciaf.usuarioDe(request), descifrar(idUsuario))));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/sesiones/{id_usuario}/cerrar/{id_sesion}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> cerrarUnaSesion(HttpServletRequest request,
            @PathVariable("id_usuario") String idUsuario, @PathVariable("id_sesion") Long idSesion) {
        return ejecutar(request, () -> {
            gestion.cerrarSesionDe(RolesSciaf.usuarioDe(request), descifrar(idUsuario), idSesion);
            return Map.of("msg", "Sesión cerrada en ese equipo");
        });
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/accesos/{id_usuario}")
    @ResponseBody
    public ResponseEntity<?> accesos(HttpServletRequest request, @PathVariable("id_usuario") String idUsuario) {
        if (!GestionUsuariosService.puedeGestionar(RolesSciaf.usuarioDe(request), request.getSession(false))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "msg", "Sin permiso."));
        }
        try {
            Long id = descifrar(idUsuario);
            Map<String, Object> r = new HashMap<>();
            r.put("ok", true);
            r.put("accesos", gestion.accesos(id, 40));
            r.put("conectado", gestion.conectado(id));
            return ResponseEntity.ok(r);
        } catch (ReglaNegocioException e) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "No se pudo leer el historial de accesos."));
        }
    }

    // ── Permisos de menú ───────────────────────────────────────────────────

    /**
     * Formulario de permisos: el árbol completo del menú (secciones → grupos → opciones),
     * pre-marcado con lo asignado o, si no tiene nada propio, con la plantilla de su rol.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/permisos/{id_usuario}")
    public String permisos(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {

        Long id = descifrar(idUsuario);
        Usuario usuario = usuarioService.findById(id);

        String rolNombre = (usuario != null && usuario.getRol() != null) ? usuario.getRol().getNombre() : "";

        Set<String> asignados = opcionMenuService.codigosPorUsuario(id);
        Set<String> plantillaRol = opcionMenuService.plantillaPorRol(rolNombre);
        // Desactivado: no tiene acceso a nada. Antes se pre-marcaba la plantilla del rol
        // (APOYO = 13 opciones) y parecía que seguía con sus permisos.
        boolean inactivo = usuario != null && !GestionUsuariosService.ACTIVO.equals(usuario.getEstado());
        Set<String> marcados = inactivo ? Set.of() : (asignados.isEmpty() ? plantillaRol : asignados);
        model.addAttribute("inactivo", inactivo);
        if (inactivo) {
            Map<String, Object> respaldo;
            try {
                respaldo = gestion.respaldo(id);
            } catch (Exception e) {
                respaldo = Map.of("hay", false, "cantidad", 0);
            }
            model.addAttribute("respaldo", respaldo);
        }

        model.addAttribute("usuario", usuario);
        model.addAttribute("idCifrado", idUsuario);
        model.addAttribute("arbol", opcionMenuService.obtenerArbolPermisos());
        model.addAttribute("marcados", marcados);
        model.addAttribute("plantillaRol", plantillaRol);
        model.addAttribute("usaPlantilla", asignados.isEmpty());
        model.addAttribute("esAdministrador", "ADMINISTRADOR".equalsIgnoreCase(rolNombre));
        model.addAttribute("conectado", gestion.conectado(id));

        return "usuario/permisos";
    }

    /**
     * Guarda los menús asignados a un usuario y los aplica en vivo a sus sesiones
     * abiertas. Una lista vacía equivale a "sin asignación explícita": vuelve a la
     * plantilla de su rol.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/guardar-permisos")
    public ResponseEntity<Map<String, Object>> guardarPermisos(
            HttpServletRequest request,
            @RequestParam("idUsuario") Long idUsuario,
            @RequestParam(value = "codigos", required = false) List<String> codigos,
            @RequestParam(value = "modo", required = false) String modo) {
        return ejecutar(request, () -> {
            boolean enLinea = gestion.conectado(idUsuario);
            String msg = gestion.guardarPermisos(RolesSciaf.usuarioDe(request), idUsuario, codigos,
                    "PLANTILLA".equals(modo));
            return Map.of("msg", msg + (enLinea && codigos != null && !codigos.isEmpty()
                    ? ". Está conectado: su menú ya cambió, sin cerrar sesión" : ""));
        });
    }

    /** JSON { ok, msg } como el resto: lo usa SciafModulo.eliminar (sciaf-modulo.js). */
    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_usuario}")
    public ResponseEntity<Map<String, Object>> eliminar(HttpServletRequest request, @PathVariable("id_usuario") String idUsuario) {
        return ejecutar(request, () -> {
            gestion.eliminar(RolesSciaf.usuarioDe(request), descifrar(idUsuario));
            return Map.of("msg", "Usuario eliminado");
        });
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    /** Revisa el permiso, ejecuta y traduce: {ok, msg, ...} siempre en JSON. */
    private ResponseEntity<Map<String, Object>> ejecutar(HttpServletRequest request,
            Supplier<Map<String, Object>> accion) {
        Map<String, Object> response = new HashMap<>();
        Usuario actor = RolesSciaf.usuarioDe(request);
        if (actor == null) {
            response.put("ok", false);
            response.put("msg", "Su sesión expiró. Vuelva a ingresar.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!GestionUsuariosService.puedeGestionar(actor, request.getSession(false))) {
            response.put("ok", false);
            response.put("msg", "No tiene permiso para administrar usuarios.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }
        try {
            response.putAll(accion.get());
            response.put("ok", true);
            return ResponseEntity.ok(response);
        } catch (ReglaNegocioException e) {
            response.put("ok", false);
            response.put("msg", e.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("msg", "Error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /** Nombre completo sin "null" cuando falta un apellido. */
    private static String nombreCompleto(Usuario u) {
        if (u.getPersona() == null) return "";
        var p = u.getPersona();
        return String.join(" ", java.util.stream.Stream.of(p.getNombre(), p.getPaterno(), p.getMaterno())
                .filter(s -> s != null && !s.isBlank()).toList()).toUpperCase(java.util.Locale.ROOT);
    }

    private Long descifrar(String idCifrado) {
        try {
            return Long.parseLong(Encriptar.decrypt(idCifrado));
        } catch (Exception e) {
            throw new ReglaNegocioException("Identificador de usuario inválido.");
        }
    }
}
