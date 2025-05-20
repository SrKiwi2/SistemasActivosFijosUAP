package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IEstadoActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;

@Service
public class EstadoActivoServiceImpl implements IEstadoActivoService{

    @Autowired private IEstadoActivoDao dao;

    @Override
    public List<EstadoActivo> findAll() {
        return dao.findAll();
    }

    @Override
    public EstadoActivo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public EstadoActivo save(EstadoActivo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<EstadoActivo> listarEstadoActivo() {
        return dao.listarEstadoActivo();
    }
    
}
