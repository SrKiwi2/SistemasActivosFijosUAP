package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

@Repository
public interface IMovimientoDao extends JpaRepository<Movimiento, Long> {
    @Query("SELECT m FROM Movimiento m WHERE m.hojaRuta.idHojaRuta = :hojaRutaId ORDER BY m.fecha DESC")
    List<Movimiento> findByHojaRuta(@Param("hojaRutaId") Long hojaRutaId);
    
    @Query("SELECT m FROM Movimiento m WHERE m.estado = :estado")
    List<Movimiento> findByEstado(@Param("estado") String estado);
    
    @Query("SELECT m FROM Movimiento m WHERE m.fecha BETWEEN :fechaInicio AND :fechaFin")
    List<Movimiento> findByFechaBetween(@Param("fechaInicio") LocalDate fechaInicio, @Param("fechaFin") LocalDate fechaFin);
    
    @Query("SELECT m FROM Movimiento m WHERE m.solicitante.idSolicitante = :solicitanteId")
    List<Movimiento> findBySolicitante(@Param("solicitanteId") Long solicitanteId);
    
    @Query("SELECT m FROM Movimiento m WHERE m.unidadOrigen.idUnidad = :unidadId OR m.unidadDestino.idUnidad = :unidadId")
    List<Movimiento> findByUnidad(@Param("unidadId") Long unidadId);
    
    @Query("SELECT m FROM Movimiento m WHERE m.hojaRuta.idHojaRuta = :hojaRutaId AND m.estado = :estado")
    List<Movimiento> findByHojaRutaAndEstado(@Param("hojaRutaId") Long hojaRutaId, @Param("estado") String estado);
}
