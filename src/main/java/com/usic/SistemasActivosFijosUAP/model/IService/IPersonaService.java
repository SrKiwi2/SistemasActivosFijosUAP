package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;

@Service
public interface IPersonaService extends IServiceGenerico<Persona, Long> {

    List<Persona> listarPersonas();

    Persona buscarPersonaPorCI(String ci);

    List<Persona> buscarPersonaPorNombrePaternoMaterno(String nombre, String paterno, String materno);

    Persona buscarPersonaPorNombreCompletoUno(String nombre, String paterno, String materno);

    Persona buscarPersonaPorNombrePaterno(String nombre, String paterno);

    Persona buscarPersonaNombre(String nombre);

    Optional<Persona> findByIdWithNacionalidadGenero(@Param("id") Long id);

    Optional<Persona> findFirstByCi(String ci);

    Page<IPersonasDao.PersonaRow> datatable(String q, Pageable pageable);

    long countActivos();

    List<Persona> buscarPorNombreApellidos(String nombre, String paterno, String materno);
}
