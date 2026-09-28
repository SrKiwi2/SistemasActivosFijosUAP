package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IHistorialBloqueoActivoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialBloqueoActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialBloqueoActivo;

@Service
public class HistorialBloqueoActivoServiceImpl implements IHistorialBloqueoActivoService {

    @Autowired
    private IHistorialBloqueoActivoDao dao;

    @Override
    public List<HistorialBloqueoActivo> findAll() {
        return dao.findAll();
    }

    @Override
    public HistorialBloqueoActivo findById(Long id) {
        return dao.findById(id).orElse(null);
    }

    @Override
    public HistorialBloqueoActivo save(HistorialBloqueoActivo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long id) {
        dao.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialBloqueoActivo> findByActivoIdActivo(Long idActivo) {
        return dao.findByActivoIdActivo(idActivo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistorialBloqueoActivo> findByResponsableIdResponsable(Long idResponsable) {
        return dao.findByResponsableIdResponsable(idResponsable);
    }
}