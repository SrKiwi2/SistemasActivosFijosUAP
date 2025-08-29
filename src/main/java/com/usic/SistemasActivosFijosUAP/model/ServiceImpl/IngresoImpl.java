package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IngresoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IIngresoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Ingreso;

@Service
public class IngresoImpl implements IngresoService{
    
    @Autowired private IIngresoDao dao;

    @Override
    public List<Ingreso> findAll() {
        return dao.findAll();
    }

    @Override
    public Ingreso findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Ingreso save(Ingreso entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<Ingreso> findAllWithTodo() {
        return dao.findAllWithTodo();
    }
}
