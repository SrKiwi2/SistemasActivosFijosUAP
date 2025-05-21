package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.dao.IPredioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

@Service
public class PredioServiceImpl implements IPredioServicio{

    @Autowired private IPredioDao dao;

    @Override
    public List<Predio> findAll() {
        return dao.findAll();
    }

    @Override
    public Predio findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Predio save(Predio entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Predio buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Predio> listarPredios() {
        return dao.listarPredios();
    }

    @Override
    public Predio buscarPorPrefijo(String prefijo) {
        return dao.buscarPorPrefijo(prefijo);
    }
    
}
