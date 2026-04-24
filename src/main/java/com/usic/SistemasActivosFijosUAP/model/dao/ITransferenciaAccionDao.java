package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaAccion;
import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaCabecera;

public interface ITransferenciaAccionDao extends JpaRepository<TransferenciaAccion, Long> {
    
    List<TransferenciaAccion> findByCabeceraOrderByFechaAccionAsc(
        TransferenciaCabecera cabecera);

    // Última acción de una transferencia
    Optional<TransferenciaAccion> findTopByCabeceraOrderByFechaAccionDesc(
        TransferenciaCabecera cabecera);

    // Acciones por tipo (ej: cuántos rechazos hubo)
    List<TransferenciaAccion> findByCabeceraAndTipoAccion(
        TransferenciaCabecera cabecera,
        TransferenciaAccion.TipoAccion tipoAccion);

    // Para estadísticas: contar por tipo en un rango de fechas
    @Query("""
        SELECT a.tipoAccion, COUNT(a)
        FROM TransferenciaAccion a
        WHERE a.fechaAccion BETWEEN :desde AND :hasta
        GROUP BY a.tipoAccion
        """)
    List<Object[]> contarPorTipoEnRango(
        @Param("desde") LocalDateTime desde,
        @Param("hasta") LocalDateTime hasta);
}
