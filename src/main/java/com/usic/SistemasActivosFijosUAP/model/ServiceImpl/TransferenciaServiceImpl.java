package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;

@Service
public class TransferenciaServiceImpl implements ITransferenciaService{

    @Autowired private ITransferenciaDao dao;

    @Override
    public List<Transferencia> findAll() {
        return dao.findAll();
    }

    @Override
    public Transferencia findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Transferencia save(Transferencia entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }
    
}
