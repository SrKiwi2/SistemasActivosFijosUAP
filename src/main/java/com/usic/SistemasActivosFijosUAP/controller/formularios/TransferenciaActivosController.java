package com.usic.SistemasActivosFijosUAP.controller.formularios;

import java.io.IOException;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestTemplate;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoTransferenciaDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.service.AiDescripcionService;
import com.usic.SistemasActivosFijosUAP.model.service.PdfTransferenciaService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;

@Controller
@RequestMapping("/trasnferencia")
@RequiredArgsConstructor
public class TransferenciaActivosController {

    private final IResponsableService responsableService;
    private final IPersonaService personaService;
    private final ICargoService cargoService;
    private final IOficinaService oficinaService;
    private final IGeneroService generoService;
    private final PdfTransferenciaService pdfTransferenciaService;
    private final IActivoService activoService;

    @PostMapping("/buscar-registrar")
    public ResponseEntity<byte[]> registrarTransferencia(
            @RequestParam String unidadOrigen,
            @RequestParam String codigoFuncionarioOrigen,
            @RequestParam String ciOrigen,
            @RequestParam String fechaTransferencia,
            @RequestParam String unidadDestino,
            @RequestParam String codigoFuncionarioDestino,
            @RequestParam String ciDestino,
            @RequestParam String fechaRecepcion,

            @RequestParam List<String>  codigoActivo,
            @RequestParam List<String>  ubicacionOrigen,
            @RequestParam List<String>  ubicacionActual) throws Exception {
        try {
            Responsable responsableOrigen = obtenerORegistrarResponsable(codigoFuncionarioOrigen, ciOrigen);
            Responsable responsableDestino = obtenerORegistrarResponsable(codigoFuncionarioDestino, ciDestino);

            List<ActivoTransferenciaDTO> activosParaPdf = new ArrayList<>();

            for (int i = 0; i < codigoActivo.size(); i++) {
                String codigo = codigoActivo.get(i);
                String ubicacionOrig = ubicacionOrigen.get(i);
                String ubicacionAct = ubicacionActual.get(i);

                Activo activo = activoService.findByCodigo(codigo)
                        .orElseThrow(() -> new RuntimeException("Activo no encontrado: " + codigo));

                ActivoTransferenciaDTO dto = new ActivoTransferenciaDTO();
                dto.setCodigo(codigo);
                dto.setDescripcion(activo.getDescripcion());
                dto.setUbicacionOrigen(ubicacionOrig);
                dto.setUbicacionActual(ubicacionAct);

                activosParaPdf.add(dto);
            }

            byte[] pdfBytes = pdfTransferenciaService.generarPdfTransferencia(
                unidadOrigen,
                responsableOrigen,
                fechaTransferencia,
                unidadDestino,
                responsableDestino,
                fechaRecepcion,
                activosParaPdf
            );

            HttpHeaders headers1 = new HttpHeaders();
            headers1.setContentType(MediaType.APPLICATION_PDF);
            headers1.setContentDisposition(ContentDisposition.inline().filename("transferencia_activos.pdf").build());
            headers1.setContentLength(pdfBytes.length);

            return new ResponseEntity<>(pdfBytes, headers1, HttpStatus.OK);

        } catch (Exception ex) {
            ex.printStackTrace();
            String errorMsg = "Error procesando: " + ex.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMsg.getBytes());
        }
    }

    private Responsable obtenerORegistrarResponsable(String codigoFuncionario, String ci) {
        Responsable responsable = responsableService.buscarPorCodigo(codigoFuncionario);
        if (responsable != null) return responsable;
    
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
    
        Oficina oficina = oficinaService.buscarPorNombre(nombreOficina);
        if (oficina == null) {
            oficina = new Oficina();
            oficina.setNombre(nombreOficina);
            oficina.setEstado("ACTIVO");
            oficina.setRegistro(new Date());
            oficina.setRegistroIdUsuario(1L);
            oficinaService.save(oficina);
        }
    
        Cargo cargo = cargoService.buscarPorNombre(nombreCargo);
        if (cargo == null) {
            cargo = new Cargo();
            cargo.setNombre(nombreCargo);
            cargo.setEstado("ACTIVO");
            cargo.setRegistro(new Date());
            cargo.setRegistroIdUsuario(1L);
            cargoService.save(cargo);
        }
    
        Responsable responsableExistente = responsableService.buscarResponsablePorPersona(persona);
        if (responsableExistente == null) {
            responsableExistente = new Responsable();
        }
    
        responsableExistente.setPersona(persona);
        responsableExistente.setCargo(cargo);
        responsableExistente.setOficina(oficina);
        responsableExistente.setCodigo_funcionario(codigoFuncionario);
        responsableExistente.setEstado("ACTIVO");
        responsableExistente.setRegistroIdUsuario(1L);
        responsableExistente.setRegistro(new Date());
    
        responsableService.save(responsableExistente);
    
        return responsableExistente;
    }

}