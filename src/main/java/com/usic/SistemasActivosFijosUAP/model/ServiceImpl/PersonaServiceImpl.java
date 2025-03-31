package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;

@Service
public class PersonaServiceImpl implements IPersonaService {
    
    @Autowired
    private IPersonasDao personaDao;

    @Override
    public List<Persona> findAll() {
        return personaDao.findAll();
    }

    @Override
    public Persona findById(Long idEntidad) {
        return personaDao.findById(idEntidad).orElse(null);
    }

    @Override
    public Persona save(Persona entidad) {
        return personaDao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        personaDao.deleteById(idEntidad);
    }

    @Override
    public List<Persona> listarPersonas() {
        return personaDao.listarPersonas();
    }

    @Override
    public Persona buscarPersonaPorCI(String ci) {
        return personaDao.buscarPersonaPorCI(ci);
    }
}
