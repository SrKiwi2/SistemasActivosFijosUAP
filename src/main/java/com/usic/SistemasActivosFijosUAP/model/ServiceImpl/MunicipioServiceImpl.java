package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.dao.IMunicipioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;

@Service
public class MunicipioServiceImpl implements IMunicipioService{

    @Autowired private IMunicipioDao dao;

    @Override
    public List<Municipio> findAll() {
        return dao.findAll();
    }

    @Override
    public Municipio findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Municipio save(Municipio entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Municipio buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Municipio> listarMunicipios() {
        return dao.listarMunicipios();
    }
    
}
