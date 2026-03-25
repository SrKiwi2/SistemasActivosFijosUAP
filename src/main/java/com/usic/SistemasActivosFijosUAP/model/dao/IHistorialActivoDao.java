package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.HistorialActivo;

public interface IHistorialActivoDao extends JpaRepository<HistorialActivo, Long>{
    
    List<HistorialActivo> findByActivoIdActivoOrderByFechaEventoDesc(Long idActivo);
    List<HistorialActivo> findByCodigoActivoOrderByFechaEventoDesc(String codigoActivo);

    @Query("SELECT h FROM HistorialActivo h WHERE h.codigoActivo IN :codigos ORDER BY h.fechaEvento DESC")
    List<HistorialActivo> findByCodigos(@Param("codigos") List<String> codigos);
 
    @Query("SELECT h FROM HistorialActivo h ORDER BY h.fechaEvento DESC")
    List<HistorialActivo> findUltimosEventos(org.springframework.data.domain.Pageable p);
 
    List<HistorialActivo> findByFechaEventoBetweenOrderByFechaEventoDesc(
        LocalDateTime desde, LocalDateTime hasta);
}
