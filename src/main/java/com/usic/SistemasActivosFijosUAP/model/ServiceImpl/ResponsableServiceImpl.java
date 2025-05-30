package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@Service
public class ResponsableServiceImpl implements IResponsableService{

    @Autowired private IResposableDao dao;

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

}
