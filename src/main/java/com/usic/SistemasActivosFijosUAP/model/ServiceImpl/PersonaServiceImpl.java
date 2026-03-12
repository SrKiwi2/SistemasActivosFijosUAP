package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao.PersonaRow;
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

    @Override
    public List<Persona> buscarPersonaPorNombrePaternoMaterno(String nombre, String paterno, String materno) {
        return personaDao.buscarPersonaPorNombrePaternoMaterno(nombre, paterno, materno);
    }

    @Override
    public Persona buscarPersonaPorNombreCompletoUno(String nombre, String paterno, String materno) {
        try {
            if (materno != null && !materno.trim().isEmpty()) {
                return personaDao.findByNombreAndPaternoAndMaterno(nombre, paterno, materno)
                        .orElse(null);
            } else {
                // Buscar solo por nombre y paterno
                return personaDao.findByNombreAndPaterno(nombre, paterno)
                        .orElse(null);
            }
        } catch (Exception e) {
            
            return null;
        }
    }

    @Override
    public Persona buscarPersonaPorNombrePaterno(String nombre, String paterno) {
        return personaDao.buscarPersonaPorNombrePaterno(nombre, paterno);
    }

    @Override
    public Persona buscarPersonaNombre(String nombre) {
        return personaDao.buscarPersonaNombre(nombre);
    }

    @Override
    public Optional<Persona> findByIdWithNacionalidadGenero(Long id) {
        return personaDao.findByIdWithNacionalidadGenero(id);
    }

    @Override
    public Optional<Persona> findFirstByCi(String ci) {
        return personaDao.findFirstByCi(ci);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PersonaRow> datatable(String q, Pageable pageable) {
        return personaDao.datatable((q != null && !q.isBlank()) ? q.trim() : null, pageable);
    }

    @Transactional(readOnly = true)
    public long countActivos() {
        return personaDao.countActivos();
    }

    @Override
    public List<Persona> buscarPorNombreApellidos(String nombre, String paterno, String materno) {
        // Búsqueda flexible: puede tener o no materno
        if (materno != null && !materno.trim().isEmpty()) {
            return personaDao.findByNombreContainingIgnoreCaseAndPaternoContainingIgnoreCaseAndMaternoContainingIgnoreCase(
                nombre, paterno, materno
            );
        } else {
            return personaDao.findByNombreContainingIgnoreCaseAndPaternoContainingIgnoreCase(
                nombre, paterno
            );
        }
    }

    @Override
    public Optional<Persona> findByCi(String ci) {
        return personaDao.findByCi(ci);
    }

}