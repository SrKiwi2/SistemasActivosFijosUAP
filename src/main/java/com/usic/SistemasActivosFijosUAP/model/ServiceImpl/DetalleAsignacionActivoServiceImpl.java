package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IDetalleAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IDetalleAsignacionDao;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DetalleAsignacionActivoServiceImpl implements IDetalleAsignacionActivoService {
    
    private final IDetalleAsignacionDao dao;

    @Override
    public List<DetalleAsignacionActivo> findAll() {
        return dao.findAll();
    }

    @Override
    public DetalleAsignacionActivo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public DetalleAsignacionActivo save(DetalleAsignacionActivo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
}
