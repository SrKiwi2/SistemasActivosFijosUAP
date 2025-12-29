package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
public class ConfiguracionRestController {
    private final IConfiguracionGestionService service;

    @GetMapping("/listar")
    public List<ConfiguracionGestion> listar() {
        return service.findAll();
    }

    // Endpoint para crear una nueva configuración desde el modal
    @ValidarUsuarioAutenticado
    @PostMapping("/crear")
    public ResponseEntity<?> crear(@Validated @RequestBody ConfiguracionGestion config) { 
        // ^^^ NOTA: Solo recibimos el objeto 'config', Spring lo llena solo.
        try {
            // Validaciones básicas (Debug)
            System.out.println("Gestion recibida: " + config.getGestion());
            System.out.println("Prefijo recibido: " + config.getPrefijoDocumento());
            System.out.println("Responsable recibido: " + config.getResponsableActivosNombre());

            // Verificar si el prefijo ya existe
            ConfiguracionGestion confiEncontrado = service.findByPrefijoDocumento(config.getPrefijoDocumento());
            
            if (confiEncontrado == null) {
                // Si es nuevo, aseguramos el estado
                if(config.getEstado() == null) config.setEstado("ACTIVO");
                
                // Guardamos el objeto que recibimos (ya tiene los datos seteados por Spring)
                ConfiguracionGestion guardado = service.save(config);
                return ResponseEntity.ok(guardado);
            } else {
                return ResponseEntity.badRequest().body("Ya existe una configuración con el prefijo: " + config.getPrefijoDocumento());
            }

        } catch (Exception e) {
            e.printStackTrace(); // Ver el error real en consola
            return ResponseEntity.badRequest().body("Error al guardar: " + e.getMessage());
        }
    }
}
