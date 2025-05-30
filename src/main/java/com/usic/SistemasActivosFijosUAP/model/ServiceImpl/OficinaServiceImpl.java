package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;

@Service
public class OficinaServiceImpl implements IOficinaService{

    @Autowired private IOficinaDao dao;

    @Override
    public List<Oficina> findAll() {
        return dao.findAll();
    }

    @Override
    public Oficina findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Oficina save(Oficina entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Oficina buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Oficina> listarOficinas() {
        return dao.listarOficinas();
    }

    @Override
    public List<Oficina> buscarPorCodigo(String codigo) {
        return dao.buscarPorCodigo(codigo);
    }

    @Override
    public List<Oficina> buscarPorNombreParcial(String termino) {
        return dao.buscarPorNombreParcial(termino);
    }
}