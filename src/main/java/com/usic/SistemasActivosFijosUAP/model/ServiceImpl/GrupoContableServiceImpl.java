package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IGrupoContableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;

@Service
public class GrupoContableServiceImpl implements IGrupoContableService{

    @Autowired private IGrupoContableDao dao;

    @Override
    public List<GrupoContable> findAll() {
        return dao.findAll();
    }

    @Override
    public GrupoContable findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public GrupoContable save(GrupoContable entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public GrupoContable buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<GrupoContable> listarGruposContables() {
        return dao.listarGruposContables();
    }

    @Override
    public GrupoContable buscarPorCodigo(Integer codigo) {
        return dao.buscarPorCodigo(codigo);
    }

    @Override
    public List<GrupoContable> saveAll(Iterable<GrupoContable> grupoContables) {
        return dao.saveAll(grupoContables);
    }

    @Override
    public Optional<GrupoContable> findByCodContable(Integer codContable) {
        return dao.findByCodContable(codContable);
    }

    @Override
    public Optional<GrupoContable> findFirstByNombreIgnoreCase(String nombre) {
        return dao.findFirstByNombreIgnoreCase(nombre);
    }

    @Override
    public List<GrupoContable> buscarPorNombreLike(String q) {
        return dao.buscarPorNombreLike(q);
    }
}