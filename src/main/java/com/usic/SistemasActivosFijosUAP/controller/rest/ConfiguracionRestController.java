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
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableEntregaService;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.ResponsableEntrega;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/configuracion")
@RequiredArgsConstructor
public class ConfiguracionRestController {
    private final IConfiguracionGestionService service;
    private final IResponsableEntregaService responsableEntregaService;

    @GetMapping("/listar")
    public List<ConfiguracionGestion> listar() {
        return service.findAll();
    }

    @PostMapping("/crear")
    public ResponseEntity<?> crear(
        @RequestParam("gestion") Integer gestion,
        @RequestParam("prefijoDocumento") String prefijoDocumento,
        @RequestParam("ciudad") String ciudad,
        @RequestParam("responsableActivosNombre") String responsableActivosNombre,
        @RequestParam(value = "responsableEntrega", required = false) String responsableEntrega,
        @RequestParam(value = "idResponsableEntregaRef", required = false) Long idResponsableEntregaRef,
        @RequestParam(value = "carpetaDrive", required = false) String carpetaDrive
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
            config.setResponsableEntrega(responsableEntrega);
            config.setResponsableEntregaRef(resolverResponsableEntrega(idResponsableEntregaRef, null));
            if (config.getResponsableEntregaRef() != null) {
                config.setResponsableEntrega(config.getResponsableEntregaRef().getNombre());
            }
            config.setCarpetaDrive(extraerIdCarpeta(carpetaDrive));
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

    /**
     * Edita campos administrables de una configuración existente (identificada por idConfig).
     * Solo actualiza los que llegan; NO toca gestión ni prefijo (clave única).
     * Pensado sobre todo para actualizar la carpeta de Drive, que cambia por gestión.
     */
    @PostMapping("/editar")
    public ResponseEntity<?> editar(
        @RequestParam("idConfig") Long idConfig,
        @RequestParam(value = "ciudad", required = false) String ciudad,
        @RequestParam(value = "estado", required = false) String estado,
        @RequestParam(value = "responsableActivosNombre", required = false) String responsableActivosNombre,
        @RequestParam(value = "responsableEntrega", required = false) String responsableEntrega,
        @RequestParam(value = "idResponsableEntregaRef", required = false) Long idResponsableEntregaRef,
        @RequestParam(value = "carpetaDrive", required = false) String carpetaDrive
    ) {
        try {
            ConfiguracionGestion config = service.findById(idConfig);
            if (config == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Configuración no encontrada"));
            }
            if (ciudad != null)                   config.setCiudad(ciudad.trim());
            if (estado != null)                   config.setEstado(estado.trim());
            if (responsableActivosNombre != null) config.setResponsableActivosNombre(responsableActivosNombre.trim());
            if (responsableEntrega != null)       config.setResponsableEntrega(responsableEntrega.trim());
            if (idResponsableEntregaRef != null)  {
                config.setResponsableEntregaRef(resolverResponsableEntrega(idResponsableEntregaRef, config.getResponsableEntregaRef()));
                if (config.getResponsableEntregaRef() != null) {
                    config.setResponsableEntrega(config.getResponsableEntregaRef().getNombre());
                }
            }
            if (carpetaDrive != null)             config.setCarpetaDrive(extraerIdCarpeta(carpetaDrive));

            return ResponseEntity.ok(service.save(config));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Acepta el ID pelado de una carpeta de Drive o un link completo
     * (p. ej. {@code https://drive.google.com/drive/folders/<ID>?usp=sharing})
     * y devuelve solo el ID. Devuelve null si la entrada está vacía.
     */
    /** Resuelve el ID a entity y sincroniza el campo String; si es null y config no nulo preserva lo que ya tiene. */
    private ResponsableEntrega resolverResponsableEntrega(Long id, ResponsableEntrega actual) {
        if (id != null) {
            ResponsableEntrega ref = responsableEntregaService.findById(id);
            if (ref != null) return ref;
        }
        return actual;
    }

    private String extraerIdCarpeta(String entrada) {
        if (entrada == null) return null;
        String s = entrada.trim();
        if (s.isEmpty()) return null;

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("/folders/([a-zA-Z0-9_-]+)").matcher(s);
        if (m.find()) return m.group(1);

        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        return s.trim();
    }
}
