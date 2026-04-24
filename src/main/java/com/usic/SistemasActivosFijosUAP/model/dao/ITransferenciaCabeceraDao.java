package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaCabecera;

public interface ITransferenciaCabeceraDao extends JpaRepository<TransferenciaCabecera, Long> {
    boolean existsByCorrT(String corrT);

    Optional<TransferenciaCabecera> findByCorrT(String corrT);

    List<TransferenciaCabecera> findByEstado(
        TransferenciaCabecera.EstadoTransferencia estado);

    // Buscar por estado para reportes/historial
    List<TransferenciaCabecera> findByEstadoOrderByFechaAccionDesc(
        TransferenciaCabecera.EstadoTransferencia estado);

    // Paginado para historial general
    @Query("""
        SELECT c FROM TransferenciaCabecera c
        WHERE (:estado IS NULL OR c.estado = :estado)
        ORDER BY c.creadoEn DESC
        """)
    Page<TransferenciaCabecera> findHistorial(
        @Param("estado") TransferenciaCabecera.EstadoTransferencia estado,
        Pageable pageable);
}
