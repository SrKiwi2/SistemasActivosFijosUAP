package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;

public interface ITranferenciaDetalleDao extends JpaRepository<TransferenciaDetalle, Long>{

    /** Transferencias en las que participó un activo (app móvil). */
    @Query("""
           select d from TransferenciaDetalle d
             join fetch d.transferencia t
             left join fetch d.oficinaAnterior oa
             left join fetch d.oficinaDestino od
             left join fetch d.responsableAnterior ra
             left join fetch ra.persona rap
            where d.activo.idActivo = :idActivo
            order by t.fechaTransferencia desc
           """)
    List<TransferenciaDetalle> historialDeActivo(@Param("idActivo") Long idActivo);
}
