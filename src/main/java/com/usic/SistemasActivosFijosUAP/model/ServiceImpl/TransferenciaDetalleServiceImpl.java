package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaDetalleService;
import com.usic.SistemasActivosFijosUAP.model.dao.ITranferenciaDetalleDao;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;

@Service
public class TransferenciaDetalleServiceImpl implements ITransferenciaDetalleService{

    @Autowired private ITranferenciaDetalleDao dao;

    @Override
    public List<TransferenciaDetalle> findAll() {
        return dao.findAll();
    }

    @Override
    public TransferenciaDetalle findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public TransferenciaDetalle save(TransferenciaDetalle entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
    
}
