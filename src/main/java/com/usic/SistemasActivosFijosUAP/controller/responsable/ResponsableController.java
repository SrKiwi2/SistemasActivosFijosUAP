package com.usic.SistemasActivosFijosUAP.controller.responsable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestTemplate;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.ResponsableRegistroDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/responsable")
@RequiredArgsConstructor
public class ResponsableController {

    private final IResponsableService responsableService;
    private final IPersonaService personaService;
    private final IOficinaService oficinaService;
    private final ICargoService cargoService;
    private final IGeneroService generoService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioResponsable() {
        return "responsable/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping(value="/api/datatables", produces=MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String,Object> apiDataTables(
        @RequestParam(name="draw",   defaultValue="1") int draw,
        @RequestParam(name="start",  defaultValue="0") int start,
        @RequestParam(name="length", defaultValue="25") int length,
        @RequestParam(name="search[value]", required=false) String search
    ) {
        int size = (length < 0) ? 1000 : length;
        int page = Math.max(start, 0) / Math.max(size, 1);
        Pageable pageable = PageRequest.of(page, size);

        Page<IResposableDao.ResponsableRow> p = responsableService.datatable(search, pageable);

        List<Map<String,Object>> data = new ArrayList<>(p.getNumberOfElements());
        for (var row : p.getContent()) {
        String idEnc;
        try { idEnc = Encriptar.encrypt(String.valueOf(row.getIdResponsable())); }
        catch (Exception e) { idEnc = ""; }

        Map<String,Object> m = new HashMap<>();
        m.put("idEnc",   idEnc);
        m.put("codFun",  nvl(row.getCodFun()));
        m.put("nombre",  nvl(row.getNombre()));
        m.put("paterno", nvl(row.getPaterno()));
        m.put("materno", nvl(row.getMaterno()));
        m.put("ci",      nvl(row.getCi()));
        m.put("oficina", nvl(row.getOficina()));
        m.put("cargo",   nvl(row.getCargo()));
        data.add(m);
        }

        long total = responsableService.countActivos(); // total sin filtro

        Map<String,Object> res = new HashMap<>();
        res.put("draw", draw);
        res.put("recordsTotal", total);
        res.put("recordsFiltered", p.getTotalElements());
        res.put("data", data);
        return res;
    }

    private static String nvl(String s){ return s==null? "" : s; }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioResponsable(Model model, Responsable responsable) {
        return "responsable/formulario";
    }


    @PostMapping("/registrar-responsable")
    public ResponseEntity<ResponsableRegistroDTO> registroResponsable(@RequestParam String codigoFuncionario, @RequestParam String ci) {
        
        Responsable responsable = responsableService.buscarPorCodigo(codigoFuncionario);
        if (responsable != null) return ResponseEntity.ok(new ResponsableRegistroDTO(responsable));
    
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
    
        Oficina oficina = oficinaService.buscarPorNombre(nombreOficina).orElseGet(() -> {
            Oficina o = new Oficina();
            o.setNombre(nombreOficina);
            o.setEstado("ACTIVO");
            o.setRegistro(new Date());
            o.setRegistroIdUsuario(1L);
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
    
        Responsable responsableExistente = responsableService.buscarResponsablePorPersona(persona);
        if (responsableExistente == null) {
            responsableExistente = new Responsable();
        }
    
        responsableExistente.setPersona(persona);
        responsableExistente.setCargo(cargo);
        responsableExistente.setOficina(oficina);
        responsableExistente.setCodigoFuncionario(codigoFuncionario);
        responsableExistente.setEstado("ACTIVO");
        responsableExistente.setRegistroIdUsuario(1L);
        responsableExistente.setRegistro(new Date());
    
        responsableService.save(responsableExistente);
    
        return ResponseEntity.ok(new ResponsableRegistroDTO(responsableExistente));
    }
}
