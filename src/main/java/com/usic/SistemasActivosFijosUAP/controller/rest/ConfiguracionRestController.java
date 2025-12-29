package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;

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

    @PostMapping("/crear")
    public ResponseEntity<?> crear(
        @RequestParam("gestion") Integer gestion,
        @RequestParam("prefijoDocumento") String prefijoDocumento,
        @RequestParam("ciudad") String ciudad,
        @RequestParam("responsableActivosNombre") String responsableActivosNombre
    ) {
        
        try {
            System.out.println("\n========== DEBUG ==========");
            System.out.println("Gestion: " + gestion);
            System.out.println("Prefijo: " + prefijoDocumento);
            System.out.println("Ciudad: " + ciudad);
            System.out.println("Responsable: " + responsableActivosNombre);
            System.out.println("=========================\n");

            if (gestion == null || gestion < 2000) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Gestión inválida"));
            }

            if (prefijoDocumento == null || prefijoDocumento.trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Prefijo vacío"));
            }

            String prefijo = prefijoDocumento.trim();

            if (service.findByPrefijoDocumento(prefijo) != null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Ya existe: " + prefijo));
            }

            ConfiguracionGestion config = new ConfiguracionGestion();
            config.setGestion(gestion);
            config.setPrefijoDocumento(prefijo);
            config.setCiudad(ciudad);
            config.setResponsableActivosNombre(responsableActivosNombre);
            config.setEstado("ACTIVO");

            ConfiguracionGestion guardado = service.save(config);
            System.out.println("✅ Guardado: ID " + guardado.getIdConfig());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(guardado);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }
}
