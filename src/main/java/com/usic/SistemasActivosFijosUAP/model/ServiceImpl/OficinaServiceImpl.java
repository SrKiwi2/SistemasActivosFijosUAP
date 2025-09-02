package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

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
    public List<Oficina> listarOficinas() {
        return dao.listarOficinas();
    }
    
    @Override
    public List<Oficina> buscarPorNombreParcial(String termino) {
        return dao.buscarPorNombreParcial(termino);
    }

    @Override
    public Optional<Oficina> findByPredioAndCodOfi(Predio predio, Short codOfi) {
        return dao.findByPredioAndCodOfi(predio, codOfi);
    }

    @Override
    public List<Oficina> saveAll(Iterable<Oficina> oficinas) {
        return dao.saveAll(oficinas);
    }

    @Override
    public Optional<Oficina> buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Oficina> buscarPorCodigo(Short codOfi) {
        return dao.buscarPorCodigo(codOfi);
    }
}