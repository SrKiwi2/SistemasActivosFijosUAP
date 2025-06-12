package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IBajaActivoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IBajaActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.BajaActivo;

@Service
public class BajaActivoServiceImpl implements IBajaActivoService{

    @Autowired private IBajaActivoDao dao;

    @Override
    public List<BajaActivo> findAll() {
        return dao.findAll();
    }

    @Override
    public BajaActivo findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public BajaActivo save(BajaActivo entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
}