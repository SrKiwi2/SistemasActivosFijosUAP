package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.dao.IPredioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

@Service
public class PredioServiceImpl implements IPredioServicio{

    @Autowired private IPredioDao dao;

    @Override
    public List<Predio> findAll() {
        return dao.findAll();
    }

    @Override
    public Predio findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Predio save(Predio entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public List<Predio> listarPredios() {
        return dao.listarPredios();
    }

    @Override
    public Optional<Predio> findByEntidadAndUnidad(Entidad entidad, String unidad) {
        return dao.findByEntidadAndUnidad(entidad, unidad);
    }

    @Override
    public List<Predio> saveAll(Iterable<Predio> predios) {
        return dao.saveAll(predios);
    }

    @Override
    public Optional<Predio> findByEntidadAndUnidadIgnoreCase(Entidad entidad, String unidad) {
        return dao.findByEntidadAndUnidadIgnoreCase(entidad, unidad);
    }
}