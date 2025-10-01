package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public Responsable buscarPorCodigo(String codigo_funcionario) {
        return dao.buscarPorCodigo(codigo_funcionario);
    }

    @Override
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
    public Responsable buscarResponsablePorPersona(Persona persona) {
        return dao.buscarResponsablePorPersona(persona);
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
    public Page<ResponsableRow> datatable(String q, Pageable pageable) {
        return dao.datatable((q!=null && !q.isBlank()) ? q.trim() : null, pageable);
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
}