package com.usic.SistemasActivosFijosUAP.controller.formularios;

import java.util.ArrayList;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IngresoService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoIngresoAjenoDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;
import com.usic.SistemasActivosFijosUAP.model.entity.Ingreso;
import com.usic.SistemasActivosFijosUAP.model.entity.IngresoDetalle;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesResponsableRepo;
import com.usic.SistemasActivosFijosUAP.model.service.PdfIngresoActivoAjenoService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/ingreso")
@RequiredArgsConstructor
public class IngresoActivosAjenosController {

    private final PdfIngresoActivoAjenoService pdfIngresoActivoAjenoService;
    private final IPersonaService personaService;
    private final IResponsableService responsableService;
    private final IGeneroService generoService;
    private final ICargoService cargoService;
    private final IOficinaService oficinaService;
    private final IPredioServicio predioServicio;
    private final IngresoService ingresoActivoAjenoService;
        private final IEntidadService entidadService;
    private final IMunicipioService municipioService;

    private final FuncionesResponsableRepo funcionesResponsableRepo;

    @PostMapping("/registrar")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<byte[]> ingresoActivosAjenos(
        @RequestParam String fechaIncorporacion,
        @RequestParam String fechaRetiro,
        @RequestParam List<String> descripcionActivo,
        @RequestParam List<String> estadoActivoAjeno,
        @RequestParam String unidadIncorpora,
        @RequestParam String codigoFuncionarioPropietario,
        @RequestParam String ciPropietario,
        @RequestParam String codigoFuncionarioAutorizador,
        @RequestParam String ciAutorizador,
        @RequestParam String nombreIdentificacion,
        @RequestParam String cargoIdentificacion,
        @RequestParam String unidadIdentificacion, HttpServletRequest request){
        
        try{
            final Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            final Responsable responsablePropietario = obtenerORegistrarResponsable(codigoFuncionarioPropietario, ciPropietario);
            final Responsable responsableAutorizador = obtenerORegistrarResponsable(codigoFuncionarioAutorizador, ciAutorizador);
            
            final Oficina oficinaIncorpora = oficinaService.buscarPorNombre(unidadIncorpora)
                .orElseThrow(() ->
                    new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No se encontró la oficina/unidad: " + unidadIncorpora
                    )
                );
            
            Ingreso ingresoActivoAjeno = new Ingreso();
            ingresoActivoAjeno.setResponsablePropietario(responsablePropietario);
            ingresoActivoAjeno.setResponsableAutoriza(responsableAutorizador);
            ingresoActivoAjeno.setOficinaPropietario(oficinaIncorpora);
            ingresoActivoAjeno.setFechaIngreso(fechaIncorporacion);
            ingresoActivoAjeno.setFechaFin(fechaRetiro);
            ingresoActivoAjeno.setRegistroIdUsuario(usuario.getIdUsuario());
            ingresoActivoAjeno.setEstado("ACTIVO");

            List<IngresoDetalle> detalles = new ArrayList<>();
            List<ActivoIngresoAjenoDTO> activosParaPdf = new ArrayList<>();

            int minSize = Math.min(descripcionActivo.size(), estadoActivoAjeno.size());

            for (int i = 0; i < minSize; i++) {
                String descripcionActivoAjeno = descripcionActivo.get(i);
                String estadoActivoA = estadoActivoAjeno.get(i);

                IngresoDetalle det = new IngresoDetalle();
                det.setIngreso(ingresoActivoAjeno);
                det.setDescripcion(descripcionActivoAjeno);
                det.setEstadoActivo(estadoActivoA);
                det.setEstado("A");
                det.setRegistroIdUsuario(usuario.getIdUsuario());
                detalles.add(det);

                ActivoIngresoAjenoDTO dto = new ActivoIngresoAjenoDTO();
                dto.setDescripcionA(descripcionActivoAjeno);
                dto.setEstadoA(estadoActivoA);
                activosParaPdf.add(dto);
            }

            ingresoActivoAjeno.setDetalles(detalles);
            ingresoActivoAjenoService.save(ingresoActivoAjeno);

            byte[] pdfBytes = pdfIngresoActivoAjenoService.generarPdfActivosAjenos(
                fechaIncorporacion,
                fechaRetiro,
                responsablePropietario,
                oficinaIncorpora.getNombre(),
                responsableAutorizador,
                nombreIdentificacion,
                cargoIdentificacion,
                unidadIdentificacion,
                activosParaPdf
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.inline().filename("ingreso_activo_ajeno.pdf").build());
            headers.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);

        } catch (Exception ex) {
            ex.printStackTrace();
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