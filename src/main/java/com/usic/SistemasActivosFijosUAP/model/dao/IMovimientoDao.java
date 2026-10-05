package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

@Repository
public interface IMovimientoDao extends JpaRepository<Movimiento, Long> {

    /**
     * Trayectoria de una hoja, el movimiento actual primero. Desempata por hora e id: con
     * solo la fecha, dos movimientos del mismo día salían en cualquier orden y el «estado
     * actual» podía ser el anterior. Las unidades vienen en la misma consulta (antes, dos
     * consultas más por movimiento).
     */
    @Query("""
        SELECT m FROM Movimiento m
        LEFT JOIN FETCH m.unidadOrigen
        LEFT JOIN FETCH m.unidadDestino
        WHERE m.hojaRuta.idHojaRuta = :hojaRutaId
        ORDER BY m.fecha DESC NULLS LAST, m.hora DESC NULLS LAST, m.idMovimiento DESC
        """)
    List<Movimiento> findByHojaRuta(@Param("hojaRutaId") Long hojaRutaId);

    /**
     * Resumen de los movimientos de una gestión (o de todas) para el listado, en el mismo
     * orden que {@link #findByHojaRuta}: el primero de cada hoja es su estado actual.
     * Columnas: id de la hoja, estado, fecha, nombre de la unidad destino.
     */
    @Query("""
        SELECT m.hojaRuta.idHojaRuta, m.estadoMovimiento, m.fecha, d.nombre
        FROM Movimiento m
        LEFT JOIN m.unidadDestino d
        WHERE (:gestion IS NULL OR m.hojaRuta.gestion = :gestion)
        ORDER BY m.fecha DESC NULLS LAST, m.hora DESC NULLS LAST, m.idMovimiento DESC
        """)
    List<Object[]> resumenParaTabla(@Param("gestion") Integer gestion);
}
