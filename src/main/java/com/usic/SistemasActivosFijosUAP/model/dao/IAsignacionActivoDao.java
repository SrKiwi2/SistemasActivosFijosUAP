package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;

public interface IAsignacionActivoDao extends JpaRepository<AsignacionActivo, Long>, JpaSpecificationExecutor<AsignacionActivo>{
    
    @Query("""
        SELECT DISTINCT a FROM AsignacionActivo a
        JOIN FETCH a.detalles d
        JOIN FETCH d.activo act
        WHERE act.estado = 'PENDIENTE'
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
