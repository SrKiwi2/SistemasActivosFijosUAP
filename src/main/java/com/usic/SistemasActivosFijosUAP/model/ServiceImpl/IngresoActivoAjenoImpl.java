package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IngresoActivoAjenoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IngresoActivoAjenoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.IngresoActivoAjeno;

@Service
public class IngresoActivoAjenoImpl implements IngresoActivoAjenoService{
    
    @Autowired private IngresoActivoAjenoDao dao;

    @Override
    public List<IngresoActivoAjeno> findAll() {
        return dao.findAll();
    }

    @Override
    public IngresoActivoAjeno findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public IngresoActivoAjeno save(IngresoActivoAjeno entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
}
