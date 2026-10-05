package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao.ResponsableRow;
import com.usic.SistemasActivosFijosUAP.model.dto.RespOption;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
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
    public Page<RespOption> searchByOficinaConCodigo(Long oficinaId, String q, Pageable pageable) {
        return dao.searchByOficinaConCodigo(oficinaId, q, pageable);
    }

    @Override
    public Page<RespOption> searchGlobalConCodigo(String q, Pageable pageable) {
        return dao.searchGlobalConCodigo(q, pageable);
    }

    @Override
    public boolean existsByOficinaIdOficinaAndPersonaIdPersona(Long idOficina, Long idPersona) {
        return dao.existsByOficinaIdOficinaAndPersonaIdPersona(idOficina, idPersona);
    }

    @Override
    public Optional<Responsable> findByCodigoFuncionarioAndOficina(String codigoFuncionario, Oficina oficina) {
        return dao.findByCodigoFuncionarioAndOficina(codigoFuncionario, oficina);
    }

    @Override
    public boolean existsByPersonaCi(String ci) {
        return dao.existsByPersonaCi(ci);
    }

    @Override
    public Optional<Responsable> findByOficinaAndPersonaCi(Oficina oficina, String ci) {
        return dao.findByOficinaAndPersonaCi(oficina, ci);
    }
}