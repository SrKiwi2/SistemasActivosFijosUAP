package com.usic.SistemasActivosFijosUAP.controller.formularios;

import java.util.Date;
import java.util.Map;

import org.springframework.http.ContentDisposition;
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
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.service.PdfGeneratorService;

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
    private final PdfGeneratorService pdfGeneratorService;

    @PostMapping("/asignar-activo-nuevo")
    public ResponseEntity<byte[]> registrarResponsable(
            @RequestParam String unidad,
            @RequestParam String codigoFuncionario,
            @RequestParam String ci,
            @RequestParam String hr,
            @RequestParam String ubicacionActivo,
            @RequestParam String descripcionActivo,
            RedirectAttributes redirectAttributes) throws Exception {

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
                String extencion = (String) data.get("cat_abreviacion");
                String nombre = (String) data.get("per_nombres");
                String paterno = (String) data.get("per_ap_paterno");
                String materno = (String) data.get("per_ap_materno");

                Persona personaci = personaService.buscarPersonaPorCI(ciPersona);
                if (personaci == null) {
                    Persona personaNPM = personaService.buscarPersonaPorNombreCompletoUno(nombre, paterno, materno);
                    if (personaNPM == null) {
                        personaNPM = new Persona();
                        personaNPM.setNombre(nombre);
                        personaNPM.setPaterno(paterno);
                        personaNPM.setMaterno(materno);
                        personaNPM.setCi(ciPersona);
                        personaNPM.setCorreo((String) data.get("perd_email_personal"));

                        Genero genero = generoService.buscarGeneroPorNombre((String) data.get("per_sexo"));
                        if (genero == null) {
                            genero = new Genero();
                            genero.setEstado("ACTIVO");
                            genero.setNombre((String) data.get("per_sexo"));
                            genero.setRegistro(new Date());
                            genero.setRegistroIdUsuario(1L);
                            generoService.save(genero);
                            personaNPM.setGenero(genero);
                        }

                        personaNPM.setEstado("ACTIVO");
                        personaNPM.setGenero(genero);
                        personaService.save(personaNPM);

                        Oficina oficina = oficinaService.buscarPorNombre((String) data.get("eo_descripcion"));
                        if (oficina == null) {
                            oficina = new Oficina();
                            oficina.setNombre((String) data.get("eo_descripcion"));
                            oficina.setEstado("ACTIVO");
                            oficina.setRegistro(new Date());
                            oficina.setRegistroIdUsuario(1L);
                            oficinaService.save(oficina);
                        }

                        Cargo cargo = cargoService.buscarPorNombre((String) data.get("p_descripcion"));
                        if (cargo == null) {
                            cargo = new Cargo();
                            cargo.setNombre(nombre);
                            cargo.setEstado("ACTIVO");
                            cargo.setRegistro(new Date());
                            cargo.setRegistroIdUsuario(1L);
                            cargoService.save(cargo);
                        }

                        Responsable responsable = responsableService.buscarResponsablePorPersona(personaNPM);
                        if (responsable == null) {
                            responsable = new Responsable();
                            responsable.setPersona(personaNPM);
                            responsable.setCargo(cargo);
                            responsable.setOficina(oficina);
                            responsable.setCodigo_funcionario(codigoFuncionario);
                            responsable.setEstado("ACTIVO");
                            responsable.setRegistroIdUsuario(1L);
                            responsable.setRegistro(new Date());
                            responsableService.save(responsable);
                        }else{
                            responsable.setPersona(personaNPM);
                            responsable.setCargo(cargo);
                            responsable.setOficina(oficina);
                            responsable.setCodigo_funcionario(codigoFuncionario);
                            responsable.setEstado("ACTIVO");
                            responsable.setRegistroIdUsuario(1L);
                            responsable.setRegistro(new Date());
                            responsableService.save(responsable);
                        }
                    }else{
                        personaNPM.setNombre(nombre);
                        personaNPM.setPaterno(paterno);
                        personaNPM.setMaterno(materno);
                        personaNPM.setCi(ciPersona);
                        personaNPM.setCorreo((String) data.get("perd_email_personal"));
                        personaService.save(personaNPM);

                        Oficina oficina = oficinaService.buscarPorNombre((String) data.get("eo_descripcion"));
                        if (oficina == null) {
                            oficina = new Oficina();
                            oficina.setNombre((String) data.get("eo_descripcion"));
                            oficina.setEstado("ACTIVO");
                            oficina.setRegistro(new Date());
                            oficina.setRegistroIdUsuario(1L);
                            oficinaService.save(oficina);
                        }

                        Cargo cargo = cargoService.buscarPorNombre((String) data.get("p_descripcion"));
                        if (cargo == null) {
                            cargo = new Cargo();
                            cargo.setNombre(nombre);
                            cargo.setEstado("ACTIVO");
                            cargo.setRegistro(new Date());
                            cargo.setRegistroIdUsuario(1L);
                            cargoService.save(cargo);
                        }

                        Responsable responsable = responsableService.buscarResponsablePorPersona(personaNPM);
                        if (responsable == null) {
                            responsable = new Responsable();
                            responsable.setPersona(personaNPM);
                            responsable.setCargo(cargo);
                            responsable.setOficina(oficina);
                            responsable.setCodigo_funcionario(codigoFuncionario);
                            responsable.setEstado("ACTIVO");
                            responsable.setRegistroIdUsuario(1L);
                            responsable.setRegistro(new Date());
                            responsableService.save(responsable);
                        }else{
                            responsable.setPersona(personaNPM);
                            responsable.setCargo(cargo);
                            responsable.setOficina(oficina);
                            responsable.setCodigo_funcionario(codigoFuncionario);
                            responsable.setEstado("ACTIVO");
                            responsable.setRegistroIdUsuario(1L);
                            responsable.setRegistro(new Date());
                            responsableService.save(responsable);
                        }
                    }
                }else{
                    redirectAttributes.addFlashAttribute("success", "Ya existe este responsable");
                }

                byte[] pdfBytes = pdfGeneratorService.generarPdfAsignacion(
                    unidad,
                    nombre + " " + paterno + " " + materno,
                    (String) data.get("p_descripcion"),
                    ciPersona,
                    (String) data.get("cat_abreviacion"),
                    ubicacionActivo,
                    descripcionActivo,
                    hr  // Añade el campo del modal al controlador
                );

                HttpHeaders headers1 = new HttpHeaders();
                headers1.setContentType(MediaType.APPLICATION_PDF);
                headers1.setContentDisposition(ContentDisposition.inline().filename("asignacion_activo_nuevo.pdf").build());

                headers1.setContentLength(pdfBytes.length);

                return new ResponseEntity<>(pdfBytes, headers1, HttpStatus.OK);
            }else {
                // API externa no devolvió OK
                String error = "Error consultando API externa.";
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error.getBytes());
            }
        } catch (Exception ex) {
            // Manejar errores internos
            String errorMsg = "Error procesando: " + ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMsg.getBytes());
        }
    }
}