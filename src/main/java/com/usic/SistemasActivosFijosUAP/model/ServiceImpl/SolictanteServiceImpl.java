package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.ISolictanteService;
import com.usic.SistemasActivosFijosUAP.model.dao.ISolicitanteDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SolictanteServiceImpl implements ISolictanteService {

    private final ISolicitanteDao dao;

    @Override
    public List<Solicitante> findAll() {
        return dao.findAll();
    }

    @Override
    public Solicitante findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Solicitante save(Solicitante entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<Solicitante> findByNombre(String nombre) {
        return dao.findByNombre(nombre);
    }

    @Override
    public List<Solicitante> findByCargo(String cargo) {
        return dao.findByCargo(cargo);
    }

    @Override
    public List<Solicitante> findByNombreContaining(String nombre) {
        return dao.findByNombreContaining(nombre);
    }
    
}
