package com.usic.SistemasActivosFijosUAP.controller;

import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/adm")
@RequiredArgsConstructor
public class IndexController {
    private final IActivoService activoService;
    private static final Logger logger = LoggerFactory.getLogger(IndexController.class);

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/inicio")
    public String VistaAdministrador(HttpServletRequest request) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        logger.info("Usuario en sesión: {}", usuario.getPersona().getNombre());
        return "inicio-admin";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/vista-administrador")
    public String inicio(HttpServletRequest request, Model model) {
        
        String rol = (String) request.getSession().getAttribute("nombre_rol");

        if (!rol.equals("RESPONSABLE")) {
            model.addAttribute("activos", Collections.emptyList()); // Omitir carga
            return "vista-admin"; // o la vista correspondiente
        }
        
        Persona persona = (Persona) request.getSession().getAttribute("persona");
        List<Activo> activos = activoService.obtenerActivosDelResponsable(persona);
        if (activos != null) {
            model.addAttribute("activos", activos);
            
        }

        return "vista-admin";
        
    }

    @GetMapping("/cargar-datos")
    @ResponseBody
    public ResponseEntity<String> cargarDatos(HttpSession session) {
        if (session.getAttribute("usuario") == null) {
            // La sesión ha expirado o no existe
            return new ResponseEntity<>("Sesión expirada", HttpStatus.UNAUTHORIZED);
        }
        // Si la sesión está activa, devuelve el contenido
        return new ResponseEntity<>("Datos del contenido", HttpStatus.OK);
    }
}
