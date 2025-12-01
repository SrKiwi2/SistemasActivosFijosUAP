package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IUnidadService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUnidadDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnidadServiceImpl implements IUnidadService {
    
    private final IUnidadDao dao;

    @Override
    public List<Unidad> findAll() {
        return dao.findAll();
    }

    @Override
    public Unidad findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Unidad save(Unidad entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Unidad> findByNombre(String nombre) {
        return dao.findByNombre(nombre);
    }

    @Override
    public List<Unidad> findByNombreContaining(String nombre) {
        return dao.findByNombreContaining(nombre);
    }
    
}
