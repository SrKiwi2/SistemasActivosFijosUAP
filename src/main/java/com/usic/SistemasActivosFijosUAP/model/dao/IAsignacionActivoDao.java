package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.dto.ResumenAsignacionDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;

public interface IAsignacionActivoDao extends JpaRepository<AsignacionActivo, Long>, JpaSpecificationExecutor<AsignacionActivo>{
    
    /**
     * Asignaciones que tienen al menos un activo PENDIENTE, con TODOS sus detalles.
     * <p>
     * El filtro va en un subselect a propósito. Ponerlo en el WHERE de un JOIN FETCH
     * —como estaba antes— no filtra las filas devueltas: filtra la colección que
     * Hibernate arma en memoria, así que {@code a.getDetalles()} quedaba con solo los
     * PENDIENTE. Consecuencia: los contadores de la vista ("sincronizados", "total de
     * activos") y cualquier suma sobre la colección salían mal.
     * <p>
     * Quien necesite solo los pendientes debe filtrarlos en Java (así lo hace
     * {@code ActivosController.tabla_registro_pendiente} al armar los items).
     */
    @Query("""
        SELECT DISTINCT a FROM AsignacionActivo a
        JOIN FETCH a.detalles d
        JOIN FETCH d.activo act
        WHERE EXISTS (
            SELECT dp.idDetalle FROM DetalleAsignacionActivo dp
            WHERE dp.asignacionActivo = a
              AND dp.activo.estado = 'PENDIENTE'
        )
        ORDER BY a.fechaAsignacion DESC
        """)
    List<AsignacionActivo> listarConDetalles();

    @Query("""
        SELECT a FROM Activo a
        WHERE a.estado = 'PENDIENTE'
        AND a NOT IN (
            SELECT d.activo FROM DetalleAsignacionActivo d
        )
        ORDER BY a.fechaUlt DESC
        """)
    List<Activo> listarPendientesSinAsignacion();

    /**
     * Actas en las que figura el activo, la más reciente primero.
     * <p>
     * Devuelve {@code List} y no {@code Optional} a propósito: la consulta puede traer
     * varias filas —nada en la base impedía que un activo estuviera en dos actas— y con
     * {@code Optional} eso no era un dato raro sino un {@code NonUniqueResultException}
     * en producción. Quien necesita una sola trabaja con la primera; el índice único
     * parcial sobre los detalles vigentes es lo que garantiza que sea la única.
     */
    @Query("""
        SELECT asg
        FROM DetalleAsignacionActivo d
        JOIN d.asignacionActivo asg
        WHERE d.activo = :activo
          AND (d.estadoDetalle IS NULL OR d.estadoDetalle = 'VIGENTE')
        ORDER BY asg.fechaAsignacion DESC
        """)
    List<AsignacionActivo> findByActivo(@Param("activo") Activo activo);

    /**
     * Bienes que coinciden con el texto, cada uno con el acta vigente donde está.
     * <p>
     * Devuelve la línea y no el activo porque lo que hace falta mostrar es justamente de
     * dónde saldría el bien: incorporarlo a un acta significa sacarlo de otra, y eso
     * tiene que verse antes de confirmar.
     */
    @Query("""
        SELECT d FROM DetalleAsignacionActivo d
        JOIN FETCH d.activo a
        JOIN FETCH d.asignacionActivo asg
        WHERE (d.estadoDetalle IS NULL OR d.estadoDetalle = 'VIGENTE')
          AND (LOWER(a.codigo) LIKE LOWER(CONCAT('%', :texto, '%'))
            OR LOWER(a.descripcion) LIKE LOWER(CONCAT('%', :texto, '%')))
          AND (:excluirActa IS NULL OR asg.idAsignacionActivo <> :excluirActa)
        ORDER BY a.codigo
        """)
    List<DetalleAsignacionActivo> buscarBienesConSuActa(@Param("texto") String texto,
                                                        @Param("excluirActa") Long excluirActa);

    /** Actas que coinciden por número o documento, para elegir destino de un traslado. */
    @Query("""
        SELECT a FROM AsignacionActivo a
        LEFT JOIN FETCH a.responsable r
        LEFT JOIN FETCH r.persona
        LEFT JOIN FETCH a.oficinaDestino
        WHERE (LOWER(a.numeroAsignacion) LIKE LOWER(CONCAT('%', :texto, '%'))
            OR LOWER(a.codigoCompleto)   LIKE LOWER(CONCAT('%', :texto, '%')))
          AND (:excluir IS NULL OR a.idAsignacionActivo <> :excluir)
        ORDER BY a.fechaAsignacion DESC
        """)
    List<AsignacionActivo> buscarActasPorTexto(@Param("texto") String texto, @Param("excluir") Long excluir);

    /** Gestiones que tienen actas, para llenar el filtro sin inventar años vacíos. */
    @Query("""
        SELECT DISTINCT YEAR(a.fechaAsignacion)
        FROM AsignacionActivo a
        WHERE a.fechaAsignacion IS NOT NULL
        ORDER BY 1 DESC
        """)
    List<Integer> gestionesConActas();

    /**
     * Totales por asignación (costo, faltantes de costo y avance hacia el VSIAF) en
     * UNA sola consulta agregada para todas las asignaciones que se estén listando.
     * Recorrer {@code asignacion.getDetalles()} fila por fila daría lo mismo pero con
     * una consulta por asignación.
     */
    /*
     * Devuelve Object[] a propósito y NO una expresión constructora de JPQL.
     * Motivo: el tipo que devuelve SUM() depende del dialecto — en PostgreSQL
     * SUM(bigint) es numeric, que Hibernate mapea a BigDecimal, no a Long. Una
     * expresión "new ...DTO(...)" exige que los tipos calcen exacto y reventaría
     * en tiempo de ejecución. El mapeo se hace en el servicio con Number.
     * Orden de las columnas: id, costoTotal, sinCosto, subidos, pendientes, conError, total.
     */
    @Query("""
        SELECT d.asignacionActivo.idAsignacionActivo,
               SUM(CASE WHEN a.costo IS NOT NULL AND a.costo > 0 THEN a.costo ELSE 0.0 END),
               SUM(CASE WHEN a.costo IS NULL OR a.costo <= 0 THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.estado = 'ACTIVO' THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.estado = 'PENDIENTE' THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.sincVsiaf = 'ERROR' THEN 1 ELSE 0 END),
               COUNT(d)
        FROM DetalleAsignacionActivo d
        JOIN d.activo a
        WHERE d.asignacionActivo.idAsignacionActivo IN :ids
          AND (d.estadoDetalle IS NULL OR d.estadoDetalle = 'VIGENTE')
        GROUP BY d.asignacionActivo.idAsignacionActivo
        """)
    List<Object[]> resumenPorAsignacion(@Param("ids") List<Long> ids);

    @Query("""
        SELECT a FROM AsignacionActivo a
        LEFT JOIN FETCH a.detalles d
        LEFT JOIN FETCH d.activo act
        LEFT JOIN FETCH act.responsable r
        LEFT JOIN FETCH r.persona
        WHERE a.idAsignacionActivo = :id
        """)
    Optional<AsignacionActivo> findByIdConDetalles(@Param("id") Long id);

    /** Para "conseguí o creá" la acta de regularización de una gestión: REG-<año>. */
    Optional<AsignacionActivo> findFirstByNumeroAsignacion(String numeroAsignacion);
}
