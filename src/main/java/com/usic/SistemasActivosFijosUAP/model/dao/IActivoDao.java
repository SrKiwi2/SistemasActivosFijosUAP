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

import com.usic.SistemasActivosFijosUAP.model.dto.CorrelativoActivoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.hardware.ActivoMantenimientoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.DivergenciaActivoDto;
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

    /**
     * Lista, para una combinación predio + grupo contable, los activos ya
     * registrados con su código, descripción y oficina, ordenados por código
     * (que dentro de un mismo prefijo equivale al orden correlativo, porque el
     * correlativo va con relleno a 5 dígitos). Alimenta la pantalla de revisión
     * de correlativos (comparación con el VSIAF).
     *
     * El predio determina al municipio (FK), por eso filtrar por predio + grupo
     * basta para fijar el prefijo {@code municipio-predio-grupo}.
     */
    @Query("""
            SELECT new com.usic.SistemasActivosFijosUAP.model.dto.CorrelativoActivoDTO(
                a.idActivo, a.codigo, a.descripcion, o.nombre, o.codOfi, a.estado)
            FROM Activo a
            JOIN a.oficina        o
            JOIN o.predio         p
            JOIN a.grupoContable  g
            WHERE p.idPredio = :predioId
              AND g.idGrupoContable = :grupoId
            ORDER BY a.codigo ASC
            """)
    List<CorrelativoActivoDTO> listarParaRevisionCorrelativo(
            @Param("predioId") Long predioId,
            @Param("grupoId")  Long grupoId);

    /**
     * Lista los activos cuyo CÓDIGO pertenece al prefijo municipio-predio-grupo,
     * sin importar dónde estén físicamente ahora (FK). Como el código es inmutable,
     * este criterio coincide con el generador de correlativos de la BD y evita los
     * falsos "huecos" que produce filtrar por la ubicación actual. Incluye el predio
     * ACTUAL para poder marcar los transferidos.
     */
    @Query("""
            SELECT new com.usic.SistemasActivosFijosUAP.model.dto.CorrelativoActivoDTO(
                a.idActivo, a.codigo, a.descripcion, o.nombre, o.codOfi, a.estado,
                p.codigo, p.descrip)
            FROM Activo a
            LEFT JOIN a.oficina o
            LEFT JOIN o.predio  p
            WHERE a.codigo LIKE CONCAT(:prefijo, '-%')
            ORDER BY a.codigo ASC
            """)
    List<CorrelativoActivoDTO> listarPorPrefijoCodigo(@Param("prefijo") String prefijo);

    /** Códigos que aparecen más de una vez (no debería haber ninguno: el código es único). */
    @Query("""
            SELECT a.codigo FROM Activo a
            WHERE a.codigo IS NOT NULL AND a.codigo <> ''
            GROUP BY a.codigo
            HAVING COUNT(a.idActivo) > 1
            ORDER BY a.codigo
            """)
    List<String> findCodigosDuplicados();

        @Query("""
    SELECT a.codigo FROM Activo a
    WHERE a.codigo = :base
       OR a.codigo LIKE CONCAT(:base, '-%')
    """)
    List<String> findCodigosByBase(@Param("base") String base);

    @Query("SELECT a.codigo FROM Activo a")
    List<String> findAllCodigos();

    /**
     * Proyección ligera para la conciliación BD↔DBF: trae solo lo necesario para
     * comparar por código y diagnosticar, sin cargar la entidad ni sus relaciones.
     * Excluye los ELIMINADO (baja lógica intencional, no son divergencias reales).
     */
    @Query("""
            SELECT new com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.DivergenciaActivoDto(
                a.idActivo, a.codigo, a.nombre, a.descripcion, a.estado, a.fechaUltimaSync)
            FROM Activo a
            WHERE a.estado NOT IN ('ELIMINADO', 'CANCELADO') AND a.codigo IS NOT NULL
            """)
    List<DivergenciaActivoDto> listarParaConciliacion();

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
