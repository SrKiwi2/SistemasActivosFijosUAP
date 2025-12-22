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
    
    @Query("SELECT r FROM Responsable r WHERE r.codigoApi = ?1 AND r.estado = 'ACTIVO'")
    Responsable buscarPorCodigo(String codigoApi);

    @Query("SELECT r FROM Responsable r WHERE r.estado = 'ACTIVO'")
    List<Responsable> listarResponsables(); //esto ya no es muy rentable, mucho resgistro de golpe, muy lento

    @Query("SELECT r FROM Responsable r WHERE r.persona = ?1 AND r.oficina =?2 AND r.cargo = ?3 AND r.estado = 'ACTIVO'")
    Responsable responsablePersonaOficinaCargo(Persona persona, Oficina oficina, Cargo cargo);

    @Query("SELECT r FROM Responsable r WHERE r.persona.idPersona = :idPersona")
    List<Responsable> findAllByPersonaIdPersona(@Param("idPersona") Long idPersona);

    List<Responsable> findAllByPersona(Persona persona);

    List<Responsable> findByPersonaAndEstado(Persona persona, String estado);

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

        Short  getCodOfi(); 
        String getEntidadCodigo();
        String getUnidadCodigo();
    }

    // total sin filtro (ACTIVO)
    @Query(value = "select count(*) from responsable r where r._estado = 'ACTIVO'", nativeQuery = true)
    long countActivos();

    @Query(value = """
        SELECT
            r.id_responsable          as idResponsable,
            r.codigo_funcionario      as codFun,
            p.nombre                  as nombre,
            p.paterno                 as paterno,
            p.materno                 as materno,
            p.ci                      as ci,
            (o.cod_ofi)::text         as oficina,
            c.nombre                  as cargo,
            o.cod_ofi                 as codOfi,
            e.entidad_codigo          as entidadCodigo,
            pr.unidad                 as unidadCodigo

        FROM responsable r
        LEFT JOIN persona  p ON p.id_persona = r.id_persona
        LEFT JOIN oficina  o ON o.id_oficina = r.id_oficina
        LEFT JOIN predio   pr ON pr.id_predio = o.id_predio  -- Join extra
        LEFT JOIN entidad  e ON e.id_entidad = pr.entidad_id -- Join extra
        LEFT JOIN cargo    c ON c.id_cargo   = r.id_cargo
        WHERE r._estado = 'ACTIVO'
            AND ( :oficinaId IS NULL OR o.id_oficina = :oficinaId )
            AND (
                :q IS NULL OR :q = '' OR
                r.codigo_funcionario      ILIKE CONCAT('%', :q, '%') OR
                p.nombre                  ILIKE CONCAT('%', :q, '%') OR
                p.paterno                 ILIKE CONCAT('%', :q, '%') OR
                p.materno                 ILIKE CONCAT('%', :q, '%') OR
                p.ci                      ILIKE CONCAT('%', :q, '%') OR
                CAST(o.cod_ofi AS TEXT)   ILIKE CONCAT('%', :q, '%') OR
                c.nombre                  ILIKE CONCAT('%', :q, '%')
            )
        ORDER BY r.id_responsable DESC
        LIMIT :#{#pageable.pageSize}
        OFFSET :#{#pageable.offset}
        """,
        countQuery = """
        SELECT count(*)
        FROM responsable r
        LEFT JOIN persona  p ON p.id_persona = r.id_persona
        LEFT JOIN oficina  o ON o.id_oficina = r.id_oficina
        LEFT JOIN cargo    c ON c.id_cargo   = r.id_cargo
        WHERE r._estado = 'ACTIVO'
            AND ( :oficinaId IS NULL OR o.id_oficina = :oficinaId )
            AND (
                :q IS NULL OR :q = '' OR
                r.codigo_funcionario      ILIKE CONCAT('%', :q, '%') OR
                p.nombre                  ILIKE CONCAT('%', :q, '%') OR
                p.paterno                 ILIKE CONCAT('%', :q, '%') OR
                p.materno                 ILIKE CONCAT('%', :q, '%') OR
                p.ci                      ILIKE CONCAT('%', :q, '%') OR
                (o.cod_ofi)::text         ILIKE CONCAT('%', :q, '%') OR
                c.nombre                  ILIKE CONCAT('%', :q, '%')
            )
        """,
        nativeQuery = true)
    Page<ResponsableRow> datatable(@Param("q") String q, @Param("oficinaId") Long oficinaId, Pageable pageable);

    /** MODLO REGISTRO RESPONSBALE */
    Optional<Responsable> findByCodigoFuncionario(String codigoFuncionario);
    
    Optional<Responsable> findByCodigoFuncionarioAndOficinaIdOficina(
        String codigoFuncionario, Long idOficina
    );
    
    List<Responsable> findByPersonaIdPersona(Long idPersona);
    
    boolean existsByPersonaIdPersona(Long idPersona);
    
    boolean existsByPersonaIdPersonaAndOficinaIdOficina(Long idPersona, Long idOficina);
    
    @Query("SELECT r FROM Responsable r WHERE " +
           "r.codigoFuncionario = :codigo AND " +
           "r.oficina.idOficina = :idOficina AND " +
           "r.estado = 'ACTIVO'")
    Optional<Responsable> findActivoByCodFuncionarioYOficina(
        @Param("codigo") String codigo, 
        @Param("idOficina") Long idOficina
    );

    @Query("SELECT r FROM Responsable r " +
           "LEFT JOIN FETCH r.persona p " +
           "LEFT JOIN FETCH r.cargo c " +
           "LEFT JOIN FETCH r.oficina o " +
           "WHERE r.idResponsable = :id")
    Optional<Responsable> findByIdWithPersonaAndCargo(@Param("id") Long id);
}