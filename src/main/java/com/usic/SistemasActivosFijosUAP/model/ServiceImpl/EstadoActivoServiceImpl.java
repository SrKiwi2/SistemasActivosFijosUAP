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

    @Override
    public EstadoActivo buscarPorCodigo(String codigo) {
        return dao.buscarPorCodigo(codigo);
    }

    @Override
    public List<EstadoActivo> listarPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return List.of();
        return dao.listarPorCodigo(codigo.trim().toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public java.util.Map<Long, Long> activosPorEstado() {
        java.util.Map<Long, Long> m = new java.util.HashMap<>();
        for (Object[] f : dao.contarActivosPorEstado()) {
            m.put(((Number) f[0]).longValue(), ((Number) f[1]).longValue());
        }
        return m;
    }
    
}
