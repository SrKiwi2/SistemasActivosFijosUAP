package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.dto.hardware.ActivoMantenimientoDTO;
import com.usic.SistemasActivosFijosUAP.model.endpoint.OficinaConteo;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;

public interface IActivoDao extends JpaRepository <Activo, Long>, JpaSpecificationExecutor<Activo>{
    
    @Query("SELECT a FROM Activo a WHERE a.nombre = ?1 AND a.estado = 'ACTIVO'")
    Activo buscarPorNombre(String nombre);

    @Query("SELECT a FROM Activo a WHERE a.codigo = ?1 AND a.estado = 'ACTIVO'")
    Activo buscarPorCodigo(String codigo);

    @Query("SELECT a FROM Activo a WHERE a.estado = 'ACTIVO'")
    List<Activo> listarActivos();

    @Query("""
    SELECT a FROM Activo a
    WHERE a.estado = 'PENDIENTE'
    AND a.idActivo NOT IN (
        SELECT d.activo.idActivo FROM DetalleAsignacionActivo d
    )
    ORDER BY a.fechaUlt DESC
    """)
    List<Activo> listarActivosPendientes();

    @Query("SELECT a FROM Activo a WHERE LOWER(a.nombre) LIKE LOWER(CONCAT('%', :filtro, '%')) OR LOWER(a.codigo) LIKE LOWER(CONCAT('%', :filtro, '%'))")
    Page<Activo> buscarPorNombreOCodigo(@Param("filtro") String filtro, Pageable pageable);

    List<Activo> findByResponsableIdResponsable(Long idResponsable);

    @Query("""
        select a.oficina as oficina, count(a) as total
        from Activo a
        join a.responsable r
        join r.persona p
        where p.idPersona = :personaId
        group by a.oficina
        order by count(a) desc
    """)
    List<OficinaConteo> conteoPorOficinaDePersona(@Param("personaId") Long personaId);

    @Query("""
        select coalesce(sum(a.costo),0)
        from Activo a
        join a.responsable r
        join r.persona p
        where p.idPersona = :personaId
    """)
    Double sumaCostoPorPersona(@Param("personaId") Long personaId);

    @EntityGraph(attributePaths = {
        "oficina.nombre", "oficina.predio.descrip", "oficina.predio.municipio.nombre",
        "grupoContable.nombre", "auxiliar.nombre", "responsable.persona.nombre", "organismoFinanciero.codOf"
    })
    Optional<Activo> findByCodigo(String codigo);

    @Query("""
        select a from Activo a
        left join fetch a.oficina o
        left join fetch o.predio p
        left join fetch p.municipio m
        left join fetch a.grupoContable gc
        left join fetch a.auxiliar aux
        left join fetch a.organismoFinanciero ofi
        left join fetch a.responsable r
        left join fetch r.persona per
        where a.codigo = :codigo
    """)
    Optional<Activo> fetchFullByCodigo(@Param("codigo") String codigo);

    Optional<Activo> findByOficinaAndCodigo(Oficina oficina, String codigo);

        @Query("""
    SELECT a.codigo FROM Activo a
    WHERE a.codigo = :base
       OR a.codigo LIKE CONCAT(:base, '-%')
    """)
    List<String> findCodigosByBase(@Param("base") String base);

    @Query("SELECT a.codigo FROM Activo a")
    List<String> findAllCodigos();

    String GRUPO_EQUIPOS_COMPUTACION = "EQUIPOS DE COMPUTACION";
    
    // =========================================================================
    // QUERY 1: Listado Paginado — Todos los Equipos de Computación
    // =========================================================================

    /**
     * Retorna una página de activos del grupo "EQUIPOS DE COMPUTACION".
     *
     * LEFT JOIN en relaciones opcionales (oficina, responsable, estadoActivo)
     * para no excluir activos con esos campos incompletos.
     *
     * JOIN (INNER) en grupoContable: es el filtro principal y DEBE existir.
     * JOIN en persona: se asume que todo Responsable tiene Persona asociada.
     *                  Si puede ser null, cambiar a LEFT JOIN r.persona p.
     */

    @Query(
        value = """
            SELECT new com.usic.SistemasActivosFijosUAP.model.dto.hardware.ActivoMantenimientoDTO(
                a.idActivo,
                a.codigo,
                a.codigoSec,
                a.nombre,
                a.descripcion,
                o.nombre,
                CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(p.paterno, '')),
                r.codigoFuncionario,
                a.fechaAdquisicion,
                a.vidaUtil,
                ea.nombre,
                a.hashDatos
            )
            FROM Activo a
            JOIN  a.grupoContable gc
            LEFT JOIN a.oficina         o
            LEFT JOIN a.responsable     r
            LEFT JOIN r.persona         p
            LEFT JOIN a.estadoActivo    ea
            WHERE UPPER(gc.nombre) = :grupoNombre
            ORDER BY a.codigo ASC
            """,
        countQuery = """
            SELECT COUNT(a)
            FROM Activo a
            JOIN a.grupoContable gc
            WHERE UPPER(gc.nombre) = :grupoNombre
            """
    )
    Page<ActivoMantenimientoDTO> findAllEquiposComputacion(
        @Param("grupoNombre") String grupoNombre,
        Pageable pageable
    );

    // =========================================================================
    // QUERY 2: Búsqueda por Código — Usa el índice idx_activo_codigo
    // =========================================================================

    /**
     * Busca un activo específico por su código, garantizando que pertenece
     * al grupo de computación. Aprovecha el índice idx_activo_codigo.
     *
     * Retorna Optional para que la capa de servicio maneje correctamente
     * el caso "no encontrado" sin lanzar excepciones en el DAO.
     */
    @Query("""
            SELECT new com.usic.SistemasActivosFijosUAP.model.dto.hardware.ActivoMantenimientoDTO(
                a.idActivo,
                a.codigo,
                a.codigoSec,
                a.nombre,
                a.descripcion,
                o.nombre,
                CONCAT(COALESCE(p.nombre, ''), ' ', COALESCE(p.paterno, '')),
                r.codigoFuncionario,
                a.fechaAdquisicion,
                a.vidaUtil,
                ea.nombre,
                a.hashDatos
            )
            FROM Activo a
            JOIN  a.grupoContable    gc
            LEFT JOIN a.oficina      o
            LEFT JOIN a.responsable  r
            LEFT JOIN r.persona      p
            LEFT JOIN a.estadoActivo ea
            WHERE a.codigo = :codigo
              AND UPPER(gc.nombre) = :grupoNombre
            """)
    Optional<ActivoMantenimientoDTO> findEquipoComputacionByCodigo(
        @Param("codigo")      String codigo,
        @Param("grupoNombre") String grupoNombre
    );
}
