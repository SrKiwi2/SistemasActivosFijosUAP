package com.usic.SistemasActivosFijosUAP.controller.formularios;

import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/asignacion")
@RequiredArgsConstructor
public class AsignacionActivoNuevoController {

    private final IPersonaService personaService;
    private final IGeneroService generoService;
    private final IOficinaService oficinaService;
    private final ICargoService cargoService;
    private final IResponsableService responsableService;

    @PostMapping("/asignar-activo-nuevo")
    public String registrarResponsable(
            @RequestParam String unidad,
            @RequestParam String codigoFuncionario,
            @RequestParam String ci,
            @RequestParam String ubicacionActivo,
            @RequestParam String descripcionActivo,
            RedirectAttributes redirectAttributes) {

        try {
            // Construir cuerpo de la solicitud
            Map<String, String> requestBody = Map.of(
                    "usuario", codigoFuncionario,
                    "contrasena", ci);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("key", "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10");

            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos",
                    request, Map.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> data = response.getBody();

                // Validar si ya existe la persona por CI
                String ciPersona = (String) data.get("per_num_doc");
                Persona persona = personaService.findByCi(ciPersona).orElseGet(() -> {
                    Persona nueva = new Persona();
                    nueva.setNombre((String) data.get("per_nombres"));
                    nueva.setPaterno((String) data.get("per_ap_paterno"));
                    nueva.setMaterno((String) data.get("per_ap_materno"));
                    nueva.setCi(ciPersona);
                    nueva.setCorreo((String) data.get("perd_email_personal"));
                    nueva.setGenero(generoService.findByCodigo((String) data.get("per_sexo"))); // "M"/"F"
                    return personaService.save(nueva);
                });

                // Buscar o crear Oficina
                Oficina oficina = oficinaService.findByNombre((String) data.get("eo_descripcion"))
                        .orElseGet(() -> {
                            Oficina o = new Oficina();
                            o.setNombre((String) data.get("eo_descripcion"));
                            return oficinaService.save(o);
                        });

                // Buscar o crear Cargo
                Cargo cargo = cargoService.findByNombre((String) data.get("p_descripcion"))
                        .orElseGet(() -> {
                            Cargo c = new Cargo();
                            c.setNombre((String) data.get("p_descripcion"));
                            return cargoService.save(c);
                        });

                // Crear Responsable
                Responsable responsable = new Responsable();
                responsable.setCodigo_funcionario((String) data.get("per_id").toString());
                responsable.setPersona(persona);
                responsable.setOficina(oficina);
                responsable.setCargo(cargo);
                responsableService.save(responsable);

                // Aquí puedes usar unidad, ubicacionActivo y descripcionActivo como desees

                redirectAttributes.addFlashAttribute("success", "Responsable registrado correctamente.");
            } else {
                redirectAttributes.addFlashAttribute("error", "Error consultando API externa.");
            }
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("error", "Error procesando: " + ex.getMessage());
        }

        return "redirect:/tu-vista-publica"; // Redirige a tu página
    }

}
