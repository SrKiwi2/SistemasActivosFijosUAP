package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionService;
import com.usic.SistemasActivosFijosUAP.model.dao.IAsignacionDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Asignacion;

@Service
public class AsignacionServiceImp implements IAsignacionService{

    @Autowired private IAsignacionDao dao;

    @Override
    public List<Asignacion> findAll() {
        return dao.findAll();
    }

    @Override
    public Asignacion findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Asignacion save(Asignacion entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
    
}
