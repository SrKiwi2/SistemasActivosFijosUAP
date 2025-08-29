package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IIngresoDetalleService;
import com.usic.SistemasActivosFijosUAP.model.dao.IIngresoDetalleDao;
import com.usic.SistemasActivosFijosUAP.model.entity.IngresoDetalle;

@Service
public class IngresoDetalleImpl implements IIngresoDetalleService{

    @Autowired private IIngresoDetalleDao dao;
    
    @Override
    public List<IngresoDetalle> findAll() {
        return dao.findAll();
    }

    @Override
    public IngresoDetalle findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public IngresoDetalle save(IngresoDetalle entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
    
}
