package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IUnidadService;
import com.usic.SistemasActivosFijosUAP.model.dao.IUnidadDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UnidadServiceImpl implements IUnidadService {
    
    private final IUnidadDao dao;

    /** Por nombre: es como se buscan en las listas. */
    @Override
    public List<Unidad> findAll() {
        return dao.findAll(Sort.by("nombre"));
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
        return dao.findFirstByNombreOrderByIdUnidadAsc(nombre);
    }
}
