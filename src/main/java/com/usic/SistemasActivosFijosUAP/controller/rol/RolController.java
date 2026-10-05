package com.usic.SistemasActivosFijosUAP.controller.rol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IRolService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Mantenimiento de roles. Las acciones de guardar y eliminar responden JSON
 * { ok, msg }, que es lo que espera la plantilla de pantallas (sciaf-modulo.js).
 */
@Controller
@RequestMapping("/administracion/rol")
@RequiredArgsConstructor
public class RolController {

    private static final int LARGO_NOMBRE = 50;

    private final IRolService rolService;
    private final IUsuarioService usuarioService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "rol/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {
        List<Rol> listaRoles = rolService.listarRoles();
        List<String> encryptedIds = new ArrayList<>();
        for (Rol roles : listaRoles) {
            String id_encryptado = Encriptar.encrypt(Long.toString(roles.getIdRol()));
            encryptedIds.add(id_encryptado);
        }

        model.addAttribute("listaRoles", listaRoles);
        model.addAttribute("id_encryptado", encryptedIds);

        return "rol/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Rol rol) {

        return "rol/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_rol}")
    public String formularioEdit(Model model, @PathVariable("id_rol") String idRol) throws Exception{
        Long id = Long.parseLong(Encriptar.decrypt(idRol));
        Rol rol = rolService.findById(id);
        model.addAttribute("rol", rol);
        model.addAttribute("edit", "true");
        if (rol != null) {
            model.addAttribute("registradoPor", nombreUsuario(rol.getRegistroIdUsuario()));
            model.addAttribute("modificadoPor", nombreUsuario(rol.getModificacionIdUsuario()));
        }
        return "rol/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-rol")
    public ResponseEntity<Map<String, Object>> registrar(HttpServletRequest request, Rol rol) {
        String nombre = normalizarNombre(rol.getNombre());
        String error = validarNombre(nombre, null);
        if (error != null) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", error));
        }

        rol.setNombre(nombre);
        rol.setEstado("ACTIVO");
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) {
            rol.setRegistroIdUsuario(usuario.getIdUsuario());
        }
        rolService.save(rol);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Rol registrado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-rol")
    public ResponseEntity<Map<String, Object>> modificar(HttpServletRequest request, Rol rol) {
        // Se modifica el rol guardado y no el que llega del formulario: ese trae solo
        // id y nombre, y al guardarlo tal cual borraba quién y cuándo lo registró.
        Rol actual = rol.getIdRol() == null ? null : rolService.findById(rol.getIdRol());
        if (actual == null || !"ACTIVO".equals(actual.getEstado())) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "El rol ya no existe o fue eliminado."));
        }

        String nombre = normalizarNombre(rol.getNombre());
        String error = validarNombre(nombre, actual.getIdRol());
        if (error != null) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", error));
        }

        actual.setNombre(nombre);
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) {
            actual.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        rolService.save(actual);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Rol modificado correctamente"));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_rol}")
    public ResponseEntity<Map<String, Object>> eliminar(HttpServletRequest request,
            @PathVariable("id_rol") String idRol) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idRol));
        Rol rol = rolService.findById(id);
        if (rol == null) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", "El rol no existe."));
        }
        rol.setEstado("ELIMINADO");
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario != null) {
            rol.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        rolService.save(rol);
        return ResponseEntity.ok(Map.of("ok", true, "msg", "Rol eliminado"));
    }

    /** Los nombres de rol se comparan tal cual en el sistema: sin espacios sobrantes y en mayúsculas. */
    private static String normalizarNombre(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    /** Mensaje de error, o null si el nombre sirve. {@code idPropio} excluye al rol que se está editando. */
    private String validarNombre(String nombre, Long idPropio) {
        if (nombre.isEmpty()) {
            return "Ingrese el nombre del rol.";
        }
        if (nombre.length() > LARGO_NOMBRE) {
            return "El nombre admite como máximo " + LARGO_NOMBRE + " caracteres.";
        }
        Rol existente = rolService.buscarRolPorNombre(nombre);
        if (existente != null && !existente.getIdRol().equals(idPropio)) {
            return "Ya existe un rol activo con el nombre " + nombre + ".";
        }
        return null;
    }

    private String nombreUsuario(Long idUsuario) {
        if (idUsuario == null) {
            return null;
        }
        return usuarioService.findByIdUsuario(idUsuario).map(Usuario::getUsuario).orElse(null);
    }
}
