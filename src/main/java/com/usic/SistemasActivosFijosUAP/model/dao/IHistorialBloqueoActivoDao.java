package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.HistorialBloqueoActivo;

public interface IHistorialBloqueoActivoDao extends JpaRepository<HistorialBloqueoActivo, Long> {

    @Query("""
        SELECT h FROM HistorialBloqueoActivo h
        WHERE h.activo.idActivo = :idActivo
        ORDER BY h.fecha DESC
        """)
    List<HistorialBloqueoActivo> findByActivoIdActivo(@Param("idActivo") Long idActivo);

    @Query("""
        SELECT h FROM HistorialBloqueoActivo h
        WHERE h.responsable.idResponsable = :idResponsable
        ORDER BY h.fecha DESC
        """)
    List<HistorialBloqueoActivo> findByResponsableIdResponsable(@Param("idResponsable") Long idResponsable);
}