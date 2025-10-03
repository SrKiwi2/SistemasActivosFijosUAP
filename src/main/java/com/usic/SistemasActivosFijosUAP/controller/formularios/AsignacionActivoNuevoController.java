package com.usic.SistemasActivosFijosUAP.controller.formularios;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionService;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Asignacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesResponsableRepo;
import com.usic.SistemasActivosFijosUAP.model.service.PdfGeneratorService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/asignacion")
@RequiredArgsConstructor
public class AsignacionActivoNuevoController {

    private final IPersonaService personaService;
    private final IGeneroService generoService;
    private final IOficinaService oficinaService;
    private final IPredioServicio predioServicio;
    private final ICargoService cargoService;
    private final IResponsableService responsableService;
    private final PdfGeneratorService pdfGeneratorService;
    private final IAsignacionService asginacionService;
    private final IEntidadService entidadService;
    private final IMunicipioService municipioService;
    private final FuncionesResponsableRepo funcionesResponsableRepo;

    @PostMapping("/asignar-activo-nuevo")
    public ResponseEntity<byte[]> registrarResponsable(
            @RequestParam String unidad,
            @RequestParam String codigoFuncionario,
            @RequestParam String ci,
            @RequestParam String hr,
            @RequestParam String ubicacionActivo,
            @RequestParam String descripcionActivo,
            RedirectAttributes redirectAttributes, HttpServletRequest request) throws Exception{

        try {
            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            System.out.println("llegó aqui");
            Responsable responsablePropietario = obtenerORegistrarResponsable(codigoFuncionario, ci);
            System.out.println("Prbando");
            Persona persona = responsablePropietario.getPersona();

            byte[] pdfBytes = pdfGeneratorService.generarPdfAsignacion(
                unidad,
                persona.getNombreCompleto(),
                responsablePropietario.getCargo().getNombre(),
                persona.getCi(),
                persona.getExtension(),
                ubicacionActivo,
                descripcionActivo,
                hr  // Añade el campo del modal al controlador
            );

            HttpHeaders headers1 = new HttpHeaders();
            headers1.setContentType(MediaType.APPLICATION_PDF);
            headers1.setContentDisposition(ContentDisposition.inline().filename("asignacion_activo_nuevo.pdf").build());
            headers1.setContentLength(pdfBytes.length);

            Asignacion asignacion = new Asignacion();
            asignacion.setUnidadResponsable(unidad);
            asignacion.setResponsable(responsablePropietario);
            asignacion.setHr(hr);
            asignacion.setUbicacionActivo(ubicacionActivo);
            asignacion.setDescripcionActivo(descripcionActivo);
            asignacion.setRegistroIdUsuario(usuario.getIdUsuario());
            asignacion.setEstado("A");
            asginacionService.save(asignacion);
            System.out.println("HOLAA");
            return new ResponseEntity<>(pdfBytes, headers1, HttpStatus.OK);

        } catch (Exception ex) {
            // Manejar errores internos
            String errorMsg = "Error procesando: " + ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMsg.getBytes());
        }
    }

    private Responsable obtenerORegistrarResponsable(String codigoFuncionario, String ci) {
        Responsable responsable = responsableService.buscarPorCodigo(codigoFuncionario);
        
        Map<String, String> requestBody = Map.of(
            "usuario", codigoFuncionario,
            "contrasena", ci
        );
    
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("key", "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10");
    
        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
    
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
            "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos",
             HttpMethod.POST,
            request,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
    
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new RuntimeException("Error consultando API externa.");
        }
    
        Map<String, Object> datos = Objects.requireNonNull(response.getBody());
        String nombre = (String) datos.get("per_nombres");
        String paterno = (String) datos.get("per_ap_paterno");
        String materno = (String) datos.get("per_ap_materno");
        String ciPersona = (String) datos.get("per_num_doc");
        String extension = (String) datos.get("cat_abreviacion");
        String correo = (String) datos.get("perd_email_personal");
        String sexo = (String) datos.get("per_sexo");
        String nombreOficina = (String) datos.get("eo_descripcion");
        String nombrePredio= (String) datos.get("cp_descripcion");
        String nombreCargo = (String) datos.get("p_descripcion");
    
        Persona persona = personaService.buscarPersonaPorCI(ciPersona);
        if (persona == null) {
            persona = personaService.buscarPersonaPorNombreCompletoUno(nombre, paterno, materno);
        }
    
        if (persona == null) {
            persona = new Persona();
            persona.setNombre(nombre);
            persona.setPaterno(paterno);
            persona.setMaterno(materno);
            persona.setCi(ciPersona);
            persona.setExtension(extension);
            persona.setCorreo(correo);
            persona.setEstado("ACTIVO");
    
            Genero genero = generoService.buscarGeneroPorNombre(sexo);
            if (genero == null) {
                genero = new Genero();
                genero.setNombre(sexo);
                genero.setEstado("ACTIVO");
                genero.setRegistro(new Date());
                genero.setRegistroIdUsuario(1L);
                generoService.save(genero);
            }
            persona.setGenero(genero);
    
            personaService.save(persona);
        }

        Predio predio = predioServicio.findByDescrip(nombrePredio)
            .orElseGet(() -> {
                Entidad entidadP = entidadService.findById(53L);
                Municipio municipio = municipioService.findById(1L);
                Predio p = new Predio();
                p.setDescrip(nombrePredio.trim());
                p.setUnidad(nombrePredio);
                p.setEntidad(entidadP);
                p.setMunicipio(municipio);
                p.setCiudad("N/D");
                p.setEstado("API");
                p.setRegistro(new Date());
                p.setRegistroIdUsuario(1L);
                return predioServicio.save(p);
            }); 

        Oficina oficina = oficinaService.buscarPorNombre(nombreOficina).orElseGet(() -> {
            short next = oficinaService.nextCodOfiForPredio(predio.getIdPredio());
            Oficina o = new Oficina();
            o.setNombre(nombreOficina.trim());
            o.setEstado("API");
            o.setRegistro(new Date());
            o.setRegistroIdUsuario(1L);
            o.setCodOfi(next);
            o.setPredio(predio);
            return oficinaService.save(o);
        });
    
        Cargo cargo = cargoService.buscarPorNombre(nombreCargo);
        if (cargo == null) {
            cargo = new Cargo();
            cargo.setNombre(nombreCargo);
            cargo.setEstado("ACTIVO");
            cargo.setRegistro(new Date());
            cargo.setRegistroIdUsuario(1L);
            cargoService.save(cargo);
        }
    
        List<Responsable> relacionados = responsableService.findByPersonaAndEstado(persona, "ACTIVO");
        if (relacionados.isEmpty()) {
            // Si NO existe ninguno y quieres crear uno "base", créalo aquí.
            // Si NO quieres crear nada cuando no hay, simplemente retorna.
            String codigo_responsable = funcionesResponsableRepo.siguienteCodigoPorOficinaStr(oficina.getIdOficina());
            Responsable r = new Responsable();
            r.setPersona(persona);
            r.setCargo(cargo);
            r.setOficina(oficina);
            r.setEstado("INACTIVO");
            r.setCodigoFuncionario(codigo_responsable);
            r.setApiEstado((short) 1);
            r.setRegistroIdUsuario(1L);
            r.setRegistro(new Date());
            return responsableService.save(r);
        }

        // 2) Actualizas SOLO lo que corresponde (campo a campo)
        for (Responsable r : relacionados) {
            // Si tu intención es solo actualizar estos campos en los relacionados:
            if (cargo != null) {
                r.setCargo(cargo);
            }
            r.setModificacion(new Date());
            r.setModificacionIdUsuario(1L);
        }
        responsableService.saveAll(relacionados);

        Optional<Responsable> exactoMismaOficina = relacionados.stream()
            .filter(r -> r.getOficina() != null && r.getOficina().getIdOficina().equals(oficina.getIdOficina()))
            .findFirst();

        if (exactoMismaOficina.isPresent()) {
            Responsable rSel = exactoMismaOficina.get();
            if (rSel.getCodigoFuncionario() == null || rSel.getCodigoFuncionario().isBlank()) {
                rSel.setCodigoFuncionario(codigoFuncionario);
            }
            rSel.setModificacion(new Date());
            rSel.setModificacionIdUsuario(1L);
            return responsableService.save(rSel);
        }

        // 6.c) No hay responsable de ESA oficina: crear uno nuevo para esa oficina y devolverlo
        Responsable nuevo = new Responsable();
        nuevo.setPersona(persona);
        nuevo.setCargo(cargo);
        nuevo.setOficina(oficina);
        nuevo.setEstado("ACTIVO");
        nuevo.setCodigoFuncionario(codigoFuncionario);
        nuevo.setApiEstado((short) 1);
        nuevo.setRegistroIdUsuario(1L);
        nuevo.setRegistro(new Date());
        return responsableService.save(nuevo);
    }
}