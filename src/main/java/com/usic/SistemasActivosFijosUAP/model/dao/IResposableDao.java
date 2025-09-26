package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

public interface IResposableDao extends JpaRepository<Responsable, Long>{
    
    @Query("SELECT r FROM Responsable r WHERE r.codigoFuncionario = ?1 AND r.estado = 'ACTIVO'")
    Responsable buscarPorCodigo(String codigoFuncionario);

    @Query("SELECT r FROM Responsable r WHERE r.estado = 'ACTIVO'")
    List<Responsable> listarResponsables();

    @Query("SELECT r FROM Responsable r WHERE r.persona = ?1 AND r.oficina =?2 AND r.cargo = ?3 AND r.estado = 'ACTIVO'")
    Responsable responsablePersonaOficinaCargo(Persona persona, Oficina oficina, Cargo cargo);

    @Query("SELECT r FROM Responsable r WHERE r.persona.idPersona = :idPersona")
    List<Responsable> findAllByPersonaIdPersona(@Param("idPersona") Long idPersona);

    List<Responsable> findAllByPersona(Persona persona);

    @Query("SELECT r FROM Responsable r WHERE r.persona = ?1 AND r.estado = 'ACTIVO'")
    Responsable buscarResponsablePorPersona(Persona persona);

    Optional<Responsable> findByOficinaAndCodigoFuncionario(Oficina oficina, String codigo_funcionario);
    Optional<Responsable> findByOficinaAndPersona(Oficina oficina, Persona persona);

    /* PARA MOSTRARA DE MANERA MAS FULL LOS REGISTROS DE RESPONSABLE */
    interface ResponsableRow {
        Long   getIdResponsable();
        String getCodFun();
        String getNombre();
        String getPaterno();
        String getMaterno();
        String getCi();
        String getOficina();
        String getCargo();
    }

    // total sin filtro (ACTIVO)
    @Query(value = "select count(*) from responsable r where r._estado = 'ACTIVO'", nativeQuery = true)
    long countActivos();

    @Query(value = """
        select
            r.id_responsable              as idResponsable,
            as_text(r.codigo_funcionario) as codFun,
            as_text(p.nombre)             as nombre,
            as_text(p.paterno)            as paterno,
            as_text(p.materno)            as materno,
            as_text(p.ci)                 as ci,
            (o.cod_ofi)::text             as oficina,         -- ← cast aquí
            as_text(c.nombre)             as cargo
        from responsable r
        left join persona  p on p.id_persona = r.id_persona
        left join oficina  o on o.id_oficina = r.id_oficina
        left join cargo    c on c.id_cargo   = r.id_cargo
        where r._estado = 'ACTIVO'
            and (
            :q is null
            or as_text(r.codigo_funcionario) ilike concat('%', :q, '%')
            or as_text(p.nombre)             ilike concat('%', :q, '%')
            or as_text(p.paterno)            ilike concat('%', :q, '%')
            or as_text(p.materno)            ilike concat('%', :q, '%')
            or as_text(p.ci)                 ilike concat('%', :q, '%')
            or (o.cod_ofi)::text             ilike concat('%', :q, '%')   -- ← y aquí
            or as_text(c.nombre)             ilike concat('%', :q, '%')
            )
        order by 2
        limit :#{#pageable.pageSize}
        offset :#{#pageable.offset}
        """,
        countQuery = """
        select count(*)
        from responsable r
        left join persona  p on p.id_persona = r.id_persona
        left join oficina  o on o.id_oficina = r.id_oficina
        left join cargo    c on c.id_cargo   = r.id_cargo
        where r._estado = 'ACTIVO'
            and (
            :q is null
            or as_text(r.codigo_funcionario) ilike concat('%', :q, '%')
            or as_text(p.nombre)             ilike concat('%', :q, '%')
            or as_text(p.paterno)            ilike concat('%', :q, '%')
            or as_text(p.materno)            ilike concat('%', :q, '%')
            or as_text(p.ci)                 ilike concat('%', :q, '%')
            or (o.cod_ofi)::text             ilike concat('%', :q, '%')   -- ← cast también aquí
            or as_text(c.nombre)             ilike concat('%', :q, '%')
            )
        """,
        nativeQuery = true)
    Page<ResponsableRow> datatable(@Param("q") String q, Pageable pageable);
}