package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IGeneroService;
import com.usic.SistemasActivosFijosUAP.model.dao.IGeneroDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Genero;

@Service
public class GeneroServiceimpl implements IGeneroService{

    @Autowired
    private IGeneroDao dao;

    @Override
    public List<Genero> findAll() {
        return dao.findAll();
    }

    @Override
    public Genero findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Genero save(Genero entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<Genero> listarGeneros() {
        return dao.listarGeneros();
    }

    @Override
    public Genero buscarGeneroPorNombre(String nombre) {
        return dao.buscarGeneroPorNombre(nombre);
    }
    
    
}
