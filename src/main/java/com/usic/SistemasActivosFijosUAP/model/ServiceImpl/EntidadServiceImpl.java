package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

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
    public Entidad buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Entidad> listarEntidad() {
        return dao.listarEntidad();
    }
    
}
