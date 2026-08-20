package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;

public interface IDetalleAsignacionDao extends JpaRepository<DetalleAsignacionActivo, Long>{

    /** Actas de asignación en las que aparece un activo (app móvil). */
    @Query("""
           select d from DetalleAsignacionActivo d
             join fetch d.asignacionActivo aa
             left join fetch aa.responsable r
             left join fetch r.persona p
             left join fetch aa.oficinaDestino o
            where d.activo.idActivo = :idActivo
            order by aa.fechaAsignacion desc
           """)
    List<DetalleAsignacionActivo> historialDeActivo(@Param("idActivo") Long idActivo);
}
