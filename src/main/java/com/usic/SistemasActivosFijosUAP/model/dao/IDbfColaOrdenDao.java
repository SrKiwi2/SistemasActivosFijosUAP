package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;

public interface IDbfColaOrdenDao extends JpaRepository<DbfColaOrden, Long> {

    /** Órdenes que todavía esperan al worker, las más viejas primero. */
    List<DbfColaOrden> findByEstadoOrderByIdOrdenAsc(String estado, Pageable pageable);

    long countByEstado(String estado);

    /**
     * Estado agregado de la cola por activo, para recalcular su marca de sincronización
     * después de resolver un lote.
     * <p>
     * Se recalcula desde el total de órdenes del activo en vez de aplicar el resultado
     * de la última: un activo puede tener un INSERT confirmado y un UPDATE posterior en
     * error, y lo que importa mostrar es que hoy está desincronizado. Las
     * {@code REINTENTADA} no cuentan: ya fueron reemplazadas por otra orden.
     * <p>
     * Columnas: idActivo, conError, pendientes, total.
     */
    @Query("""
        SELECT o.idActivo,
               SUM(CASE WHEN o.estado = 'ERROR'    THEN 1 ELSE 0 END),
               SUM(CASE WHEN o.estado = 'ENCOLADA' THEN 1 ELSE 0 END),
               COUNT(o)
        FROM DbfColaOrden o
        WHERE o.idActivo IN :ids
          AND o.estado <> 'REINTENTADA'
        GROUP BY o.idActivo
        """)
    List<Object[]> resumenPorActivo(@Param("ids") List<Long> ids);

    /** Órdenes de un activo, de la más reciente a la más vieja. */
    List<DbfColaOrden> findByIdActivoOrderByIdOrdenDesc(Long idActivo);
}
