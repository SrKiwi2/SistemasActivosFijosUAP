package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.dao.IEntidadDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

@Service
public class EntidadServiceImpl implements IEntidadService{

    @Autowired private IEntidadDao dao;

    @Override
    public List<Entidad> findAll() {
        return dao.findAll();
    }

    @Override
    public Entidad findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Entidad save(Entidad entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Entidad> findByGestionAndEntidadCodigo(Short gestion, String entidadCodigo) {
        return dao.findByGestionAndEntidadCodigo(gestion, entidadCodigo);
    }

    @Override
    public List<Entidad> saveAll(Iterable<Entidad> entidades) {
        return dao.saveAll(entidades);
    }

    @Override
    public Optional<Entidad> findTopByEntidadCodigoOrderByGestionDesc(String entidadCodigo) {
        return dao.findTopByEntidadCodigoOrderByGestionDesc(entidadCodigo);
    }    
}