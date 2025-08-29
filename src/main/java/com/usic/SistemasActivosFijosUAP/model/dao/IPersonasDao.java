package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Persona;

public interface IPersonasDao extends JpaRepository <Persona, Long>{
    @Query("SELECT p FROM Persona p WHERE p.ci = ?1 AND p.estado = 'ACTIVO'")
    Persona buscarPersonaPorCI(String ci);

    @Query("SELECT p FROM Persona p WHERE p.estado = 'ACTIVO'")
    List<Persona> listarPersonas();

    @Query("SELECT p FROM Persona p WHERE p.nombre = ?1 AND p.paterno = ?2 AND p.materno = ?3 AND p.estado = 'ACTIVO'")
    List<Persona> buscarPersonaPorNombrePaternoMaterno(String nombre, String paterno, String materno);

    @Query("SELECT p FROM Persona p WHERE p.nombre = ?1 AND p.paterno = ?2 AND p.materno = ?3 AND p.estado = 'ACTIVO'")
    Persona buscarPersonaPorNombreCompletoUno(String nombre, String paterno, String materno);

    @Query("SELECT p FROM Persona p WHERE p.nombre = ?1 AND p.paterno = ?2 AND p.estado = 'ACTIVO'")
    Persona buscarPersonaPorNombrePaterno(String nombre, String paterno);

    @Query("SELECT p FROM Persona p WHERE p.nombre = ?1 AND p.estado = 'ACTIVO'")
    Persona buscarPersonaNombre(String nombre);

    @Query("""
        select p
        from Persona p
        left join fetch p.nacionalidad
        left join fetch p.genero
        where p.idPersona = :id
    """)
    Optional<Persona> findByIdWithNacionalidadGenero(@Param("id") Long id);
}
