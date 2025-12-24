package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;

import io.swagger.v3.oas.annotations.parameters.RequestBody;
import lombok.RequiredArgsConstructor;

@RestController // 🟢 IMPORTANTE: @RestController, no @Controller
@RequestMapping("/api/configuracion") // 🟢 Verifica esta ruta base
@RequiredArgsConstructor
public class ConfiguracionRestController {
    private final IConfiguracionGestionService service;

    @GetMapping("/listar")
    public List<ConfiguracionGestion> listar() {
        return service.findAll();
    }

    // Endpoint para crear una nueva configuración desde el modal
    @PostMapping("/crear")
    public ResponseEntity<?> crear(@RequestBody ConfiguracionGestion config) {
        try {
            // Validaciones básicas
            if (config.getGestion() == null) config.setGestion(LocalDate.now().getYear());
            if (config.getPrefijoDocumento() == null || config.getPrefijoDocumento().isBlank()) {
                return ResponseEntity.badRequest().body("El prefijo es obligatorio");
            }
            
            ConfiguracionGestion guardada = service.save(config);
            return ResponseEntity.ok(guardada);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error al guardar: " + e.getMessage());
        }
    }
}
