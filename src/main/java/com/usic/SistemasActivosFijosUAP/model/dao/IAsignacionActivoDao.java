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

    @Query("""
        SELECT d.asignacionActivo
        FROM DetalleAsignacionActivo d
        WHERE d.activo = :activo
        """)
    Optional<AsignacionActivo> findByActivo(@Param("activo") Activo activo);

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
     * Orden de las columnas: id, costoTotal, sinCosto, subidos, pendientes, total.
     */
    @Query("""
        SELECT d.asignacionActivo.idAsignacionActivo,
               SUM(CASE WHEN a.costo IS NOT NULL AND a.costo > 0 THEN a.costo ELSE 0.0 END),
               SUM(CASE WHEN a.costo IS NULL OR a.costo <= 0 THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.estado = 'ACTIVO' THEN 1 ELSE 0 END),
               SUM(CASE WHEN a.estado = 'PENDIENTE' THEN 1 ELSE 0 END),
               COUNT(d)
        FROM DetalleAsignacionActivo d
        JOIN d.activo a
        WHERE d.asignacionActivo.idAsignacionActivo IN :ids
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
}
