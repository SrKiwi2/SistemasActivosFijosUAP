package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.dao.ICargoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;

@Service
public class CargoServiceImpl implements ICargoService{

    @Autowired private ICargoDao dao;

    @Override
    public List<Cargo> findAll() {
        return dao.findAll();
    }

    @Override
    public Cargo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Cargo save(Cargo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Cargo buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Cargo> listarCargos() {
        return dao.listarCargos();
    }
    
}
