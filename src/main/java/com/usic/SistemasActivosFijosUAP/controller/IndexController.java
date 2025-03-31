package com.usic.SistemasActivosFijosUAP.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/adm")
public class IndexController {
    
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
    public String inicio() {
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
