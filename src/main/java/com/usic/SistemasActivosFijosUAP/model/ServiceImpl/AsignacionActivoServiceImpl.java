package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AsignacionActivoServiceImpl implements IAsignacionActivoService {
    private final IAsignacionActivoDao dao;

    @Override
    public List<AsignacionActivo> findAll() {
        return dao.findAll();
    }

    @Override
    public AsignacionActivo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public AsignacionActivo save(AsignacionActivo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<AsignacionActivo> listarConDetalles() {
        return dao.listarConDetalles();
    }

    @Override
    public List<Activo> listarPendientesSinAsignacion() {
        return dao.listarPendientesSinAsignacion();
    }

    @Override
    public Optional<AsignacionActivo> findByActivo(Activo activo) {
        return dao.findByActivo(activo);
    }

    @Override
    public Optional<AsignacionActivo> findByIdConDetalles(Long id) {
        return dao.findByIdConDetalles(id);
    }
}
