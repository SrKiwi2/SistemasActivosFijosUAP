package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    Optional<Persona> findFirstByCi(String ci);

    /* para cargar la tabla de persona mas rapido?*/
    // Proyección para enviar solo lo necesario a la tabla
    interface PersonaRow {
        Long getIdPersona();
        String getNombre();
        String getPaterno();
        String getMaterno();
        String getCi();
    }

    @Query(
    value = """
        select
        p.id_persona  as idPersona,
        p.nombre      as nombre,
        p.paterno     as paterno,
        p.materno     as materno,
        p.ci          as ci
        from persona p
        where p._estado = 'ACTIVO'
        and (
            :q is null
            or p.nombre  ilike concat('%', :q, '%')
            or p.paterno ilike concat('%', :q, '%')
            or p.materno ilike concat('%', :q, '%')
            or p.ci      ilike concat('%', :q, '%')
        )
        order by 2
        limit :#{#pageable.pageSize}
        offset :#{#pageable.offset}
    """,
    countQuery = """
        select count(*)
        from persona p
        where p._estado = 'ACTIVO'
        and (
            :q is null
            or p.nombre  ilike concat('%', :q, '%')
            or p.paterno ilike concat('%', :q, '%')
            or p.materno ilike concat('%', :q, '%')
            or p.ci      ilike concat('%', :q, '%')
        )
    """,
    nativeQuery = true
    )
    Page<PersonaRow> datatable(@Param("q") String q, Pageable pageable);

    // Total sin filtro para DataTables
    @Query(value = "select count(*) from persona p where p._estado = 'ACTIVO'", nativeQuery = true)
    long countActivos();

    List<Persona> findByNombreContainingIgnoreCaseAndPaternoContainingIgnoreCaseAndMaternoContainingIgnoreCase(
        String nombre, String paterno, String materno
    );
    
    List<Persona> findByNombreContainingIgnoreCaseAndPaternoContainingIgnoreCase(
        String nombre, String paterno
    );

    Optional<Persona> findByNombreAndPaternoAndMaterno(String nombre, String paterno, String materno);
    Optional<Persona> findByNombreAndPaterno(String nombre, String paterno);

}

