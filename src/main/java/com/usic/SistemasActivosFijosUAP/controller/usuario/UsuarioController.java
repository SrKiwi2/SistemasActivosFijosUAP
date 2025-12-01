package com.usic.SistemasActivosFijosUAP.controller.usuario;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IRolService;
import com.usic.SistemasActivosFijosUAP.model.IService.IUsuarioService;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Rol;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.GeneradorUsuarios;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/usuario")
@RequiredArgsConstructor
public class UsuarioController {

    private final PasswordEncoder passwordEncoder;
    
    private final IUsuarioService usuarioService;
    private final IPersonaService personaService;
    private final IRolService rolService;
    private final GeneradorUsuarios generadorUsuarios;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio() {
        return "usuario/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model) throws Exception {

        List<Usuario> listaUsuarios = usuarioService.listarUsuarios();
        List<String> encryptedIds = new ArrayList<>();
        for (Usuario usuarios : listaUsuarios) {
            String id_encryptado = Encriptar.encrypt(Long.toString(usuarios.getIdUsuario()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listaUsuarios", listaUsuarios);
        model.addAttribute("id_encryptado", encryptedIds);

        return "usuario/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario(Model model, Usuario usuario) {
        model.addAttribute("usuario", new Usuario());
        model.addAttribute("listaPersonas", personaService.listarPersonas());
        model.addAttribute("listaRoles", rolService.listarRoles());
        model.addAttribute("edit", false);
        return "usuario/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_usuario}")
    public String formularioEdit(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idUsuario));
        model.addAttribute("usuario", usuarioService.findById(id));
        model.addAttribute("listaPersonas", personaService.listarPersonas());
        model.addAttribute("listaRoles", rolService.listarRoles());
        model.addAttribute("edit", "true");

        return "usuario/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-usuario")
    public ResponseEntity<Map<String, Object>> registrar(
            HttpServletRequest request,
            @RequestParam("usuario") String nombreUsuario,
            @RequestParam("password") String password,
            @RequestParam("persona.idPersona") Long idPersona,
            @RequestParam("rol.idRol") Long idRol) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Crear el objeto Usuario manualmente
            Usuario usuario = new Usuario();
            usuario.setUsuario(nombreUsuario);
            usuario.setPassword(passwordEncoder.encode(password));
            usuario.setEstado("ACTIVO");
            
            // Obtener el usuario logueado
            Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
            usuario.setRegistroIdUsuario(usuarioLogueado.getIdUsuario());
            
            // Cargar las entidades completas desde la BD
            Persona persona = personaService.findById(idPersona);
            if (persona == null) {
                response.put("ok", false);
                response.put("msg", "La persona seleccionada no existe");
                return ResponseEntity.badRequest().body(response);
            }
            
            Rol rol = rolService.findById(idRol);
            if (rol == null) {
                response.put("ok", false);
                response.put("msg", "El rol seleccionado no existe");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Validar que no exista el usuario
            if (usuarioService.UsuarioyContraseña(nombreUsuario, password) != null) {
                response.put("ok", false);
                response.put("msg", "Ya existe el usuario y contraseña");
                return ResponseEntity.ok(response);
            }
            
            usuario.setPersona(persona);
            usuario.setRol(rol);
            
            // Guardar
            usuarioService.save(usuario);
            
            response.put("ok", true);
            response.put("msg", "Se realizó el registro correctamente");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace(); // Para ver el error completo en la consola
            response.put("ok", false);
            response.put("msg", "Error al registrar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
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
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Obtener el usuario existente
            Usuario usuarioExistente = usuarioService.findById(idUsuario);
            
            if (usuarioExistente == null) {
                response.put("ok", false);
                response.put("msg", "El usuario no existe");
                return ResponseEntity.badRequest().body(response);
            }
            
            // Obtener el usuario logueado
            Usuario usuarioLogueado = (Usuario) request.getSession().getAttribute("usuario");
            
            // Actualizar campos
            usuarioExistente.setUsuario(nombreUsuario);
            
            // Solo actualizar password si se proporcionó uno nuevo
            if (password != null && !password.isEmpty()) {
                usuarioExistente.setPassword(passwordEncoder.encode(password));
            }
            
            // Cargar entidades completas
            Persona persona = personaService.findById(idPersona);
            if (persona == null) {
                response.put("ok", false);
                response.put("msg", "La persona seleccionada no existe");
                return ResponseEntity.badRequest().body(response);
            }
            
            Rol rol = rolService.findById(idRol);
            if (rol == null) {
                response.put("ok", false);
                response.put("msg", "El rol seleccionado no existe");
                return ResponseEntity.badRequest().body(response);
            }
            
            usuarioExistente.setPersona(persona);
            usuarioExistente.setRol(rol);
            usuarioExistente.setModificacionIdUsuario(usuarioLogueado.getIdUsuario());
            
            // Guardar
            usuarioService.save(usuarioExistente);
            
            response.put("ok", true);
            response.put("msg", "Se realizó la modificación correctamente");
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("ok", false);
            response.put("msg", "Error al modificar: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_usuario}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_usuario") String idUsuario) throws Exception {

        Long id = Long.parseLong(Encriptar.decrypt(idUsuario));
        Usuario usuario = usuarioService.findById(id);
        usuario.setEstado("ELIMINADO");
        usuarioService.save(usuario);

        return ResponseEntity.ok("Registro Eliminado");
    }

    @PostMapping("/generar-usuarios")
    public void generarUsuarios(HttpServletResponse response) throws IOException {
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
}
