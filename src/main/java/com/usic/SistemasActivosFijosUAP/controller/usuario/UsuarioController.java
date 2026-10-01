package com.usic.SistemasActivosFijosUAP.controller.usuario;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

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
import com.usic.SistemasActivosFijosUAP.model.service.GeneradorUsuarios;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.AuditoriaPermisosService;
import com.usic.SistemasActivosFijosUAP.model.service.seguridad.GestionUsuariosService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final GeneradorUsuarios generadorUsuarios;
    private final GestionUsuariosService gestion;
    private final AuditoriaPermisosService auditoria;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "usuario/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model, HttpServletRequest request) {
        List<UsuarioFilaDto> filas = gestion.listar(RolesSciaf.usuarioDe(request));
        model.addAttribute("filas", filas);
        model.addAttribute("total", filas.size());
        model.addAttribute("activos", filas.stream().filter(f -> "ACTIVO".equals(f.getEstado())).count());
        model.addAttribute("inactivos", filas.stream().filter(f -> !"ACTIVO".equals(f.getEstado())).count());
        model.addAttribute("conectados", filas.stream().filter(UsuarioFilaDto::isConectado).count());
        model.addAttribute("conFallidos", filas.stream().filter(f -> f.getFallidosRecientes() >= 3).count());
        model.addAttribute("roles", rolService.listarRoles());
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
            gestion.registrar(RolesSciaf.usuarioDe(request), nombreUsuario, password,
                    confirmacion != null ? confirmacion : password, idPersona, idRol);
            return Map.of("msg", "Se realizó el registro correctamente. Asígnele sus permisos con el botón «Permisos»");
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

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_usuario}")
    public ResponseEntity<String> eliminar(HttpServletRequest request, @PathVariable("id_usuario") String idUsuario) {
        Usuario actor = RolesSciaf.usuarioDe(request);
        if (!GestionUsuariosService.puedeGestionar(actor, request.getSession(false))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No tiene permiso para eliminar usuarios.");
        }
        try {
            gestion.eliminar(actor, descifrar(idUsuario));
            return ResponseEntity.ok("Registro Eliminado");
        } catch (ReglaNegocioException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("No se pudo eliminar.");
        }
    }

    /**
     * Alta masiva de usuarios con contraseña simple (CSV). Estaba abierto a cualquiera,
     * sin sesión: ahora exige ADMINISTRADOR.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/generar-usuarios")
    public void generarUsuarios(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!RolesSciaf.esAdministrador(RolesSciaf.usuarioDe(request))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Solo un ADMINISTRADOR puede generar usuarios.");
            return;
        }
        List<String[]> credenciales = generadorUsuarios.generarUsuariosMasivos();

        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"usuarios_generados.csv\"");

        try (PrintWriter writer = response.getWriter()) {
            writer.println("usuario,contrasena");
            for (String[] credencial : credenciales) {
                writer.printf("%s,%s\n", credencial[0], credencial[1]);
            }
        }
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

    private Long descifrar(String idCifrado) {
        try {
            return Long.parseLong(Encriptar.decrypt(idCifrado));
        } catch (Exception e) {
            throw new ReglaNegocioException("Identificador de usuario inválido.");
        }
    }
}
