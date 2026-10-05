package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.dao.IMunicipioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPredioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;

@Service
public class MunicipioServiceImpl implements IMunicipioService{

    @Autowired private IMunicipioDao dao;
    @Autowired private IPredioDao predioDao;

    @Override
    public List<Municipio> findAll() {
        return dao.findAll();
    }

    @Override
    public Municipio findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public Municipio save(Municipio entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Municipio buscarPorNombre(String nombre) {
        return dao.buscarPorNombre(nombre);
    }

    @Override
    public List<Municipio> listarMunicipios() {
        return dao.listarMunicipios();
    }

    @Override
    public List<Municipio> conNombreOCodigo(String nombre, String codigo) {
        return dao.conNombreOCodigo(nombre == null ? "" : nombre, codigo == null ? "" : codigo);
    }

    @Override
    public Map<Long, Long> prediosPorMunicipio() {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] f : predioDao.contarPorMunicipio()) {
            m.put(((Number) f[0]).longValue(), ((Number) f[1]).longValue());
        }
        return m;
    }
    
}
