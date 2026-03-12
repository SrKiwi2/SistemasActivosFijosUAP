package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao.ResponsableRow;
import com.usic.SistemasActivosFijosUAP.model.dto.RespOption;
import com.usic.SistemasActivosFijosUAP.model.dto.responsable.ResponsableApiDataDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesResponsableRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResponsableServiceImpl implements IResponsableService{

    private final IResposableDao dao;
    private final FuncionesResponsableRepo repo;
    private final IPersonaService personaService;
    private final IGeneroService generoService;
    private final ICargoService cargoService;

    @Override
    public List<Responsable> findAll() {
       return dao.findAll();
    }

    @Override
    public Responsable findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Responsable save(Responsable entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Responsable buscarPorCodigo(String codigoApi) {
        return dao.buscarPorCodigo(codigoApi);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Responsable> listarResponsables() {
        return dao.listarResponsables();
    }

    @Override
    public Responsable responsablePersonaOficinaCargo(Persona persona, Oficina oficina, Cargo cargo) {
        return dao.responsablePersonaOficinaCargo(persona, oficina, cargo);
    }

    @Override
    public List<Responsable> findAllByPersonaIdPersona(Long idPersona) {
        return dao.findAllByPersonaIdPersona(idPersona);
    }

    @Override
    public List<Responsable> findAllByPersona(Persona persona) {
        return dao.findAllByPersona(persona);
    }

    @Override
    public Optional<Responsable> findByOficinaAndCodigoFuncionario(Oficina oficina, String codigo_funcionario) {
        return dao.findByOficinaAndCodigoFuncionario(oficina, codigo_funcionario);
    }

    @Override
    public Optional<Responsable> findByOficinaAndPersona(Oficina oficina, Persona persona) {
        return dao.findByOficinaAndPersona(oficina, persona);
    }

    @Override
    public List<Responsable> saveAll(Iterable<Responsable> responsables) {
        return dao.saveAll(responsables);
    }

    @Override
    public Page<ResponsableRow> datatable(String q, Long oficinaId, Pageable pageable) {
        return dao.datatable((q!=null && !q.isBlank()) ? q.trim() : null, oficinaId, pageable);
    }

    @Override
    public long countActivos() {
        return dao.countActivos();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RespOption> search(String term, Pageable pageable) {
        return repo.search(term, pageable);
    }

    @Override
    public List<Responsable> findByPersonaAndEstado(Persona persona, String estado) {
        return dao.findByPersonaAndEstado(persona, estado);
    }

    @Override
    public ResponsableApiDataDTO getResponsableDataFromApi(String codigoFuncionarioApi, String ci) {
        
        Map<String, Object> datos = consumirApiExterna(codigoFuncionarioApi, ci);

        String nombre = (String) datos.get("per_nombres");
        String paterno = (String) datos.get("per_ap_paterno");
        String materno = (String) datos.get("per_ap_materno");
        String ciPersona = (String) datos.get("per_num_doc");
        String correo = (String) datos.get("perd_email_personal");
        String sexo = (String) datos.get("per_sexo");
        String nombreCargo = (String) datos.get("p_descripcion");

        Persona persona = personaService.buscarPersonaPorCI(ciPersona);
        if (persona == null) {
            persona = personaService.buscarPersonaPorNombreCompletoUno(nombre, paterno, materno);
        }
        
        Genero genero = generoService.buscarGeneroPorNombre(sexo);
        if (genero == null) {
            genero = new Genero();
            genero.setNombre(sexo);
            genero.setEstado("ACTIVO");
            generoService.save(genero);
        }

        Cargo cargo = cargoService.buscarPorNombre(nombreCargo);
        if (cargo == null) {
            cargo = new Cargo();
            cargo.setNombre(nombreCargo);
            cargo.setEstado("ACTIVO");
            cargoService.save(cargo);
        }
        
        ResponsableApiDataDTO dto = new ResponsableApiDataDTO();
        dto.setNombre(nombre);
        dto.setPaterno(paterno);
        dto.setMaterno(materno);
        dto.setCi(ciPersona);
        dto.setCorreo(correo);
        dto.setNombreCargoApi(nombreCargo);
        dto.setYaEsResponsable(false);

        return dto;
    }

    private Map<String, Object> consumirApiExterna(String codigoFuncionarioApi, String ci) {

        RestTemplate restTemplate = new RestTemplate();
        Map<String, String> requestBody = Map.of("usuario", codigoFuncionarioApi, "contrasena", ci);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("key", "e73b1991c59a67fe182524e4d12da556136ced8a9da310c3af4c4efbde804a10");
        
        HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "http://virtual.uap.edu.bo:7174/api/londraPost/v1/obtenerDatos",
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<Map<String, Object>>() {});

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
             throw new RuntimeException("Error consultando API externa.");
        }
        return Objects.requireNonNull(response.getBody());
    }

    @Override
    public Responsable findByCodigoFuncionario(String codigoFuncionario) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findByCodigoFuncionario'");
    }

    @Override
    public Responsable findByCodigoFuncionarioYOficina(String codigoFuncionario, Long idOficina) {
        return dao.findByCodigoFuncionarioAndOficinaIdOficina(
            codigoFuncionario, idOficina
        ).orElse(null);
    }

    @Override
    public boolean existeResponsablePorPersona(Long idPersona) {
       return dao.existsByPersonaIdPersona(idPersona);
    }

    @Override
    public boolean existeResponsablePorPersonaYOficina(Long idPersona, Long idOficina) {
        return dao.existsByPersonaIdPersonaAndOficinaIdOficina(idPersona, idOficina);
    }

    @Override
    public List<Responsable> findByPersonaId(Long idPersona) {
        return dao.findByPersonaIdPersona(idPersona);
    }

    @Override
    @Transactional(readOnly = true)
    public Responsable findByIdWithRelations(Long id) {
        return dao.findByIdWithPersonaAndCargo(id)
                .orElse(null);
    }

    @Override
    public Page<RespOption> searchByOficina(Long oficinaId, String q, Pageable pageable) {
        return dao.searchByOficina(oficinaId, q, pageable);
    }

    @Override
    public List<Responsable> findByOficinaIdOficina(Long idOficina) {
        return dao.findByOficinaIdOficina(idOficina);
    }

    @Override
    public Page<RespOption> searchGlobal(String q, Pageable pageable) {
        return dao.searchGlobal(q, pageable);
    }

    @Override
    public boolean existsByOficinaIdOficinaAndPersonaIdPersona(Long idOficina, Long idPersona) {
        return dao.existsByOficinaIdOficinaAndPersonaIdPersona(idOficina, idPersona);
    }
}