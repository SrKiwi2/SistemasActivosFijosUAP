package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.HistorialPermisoUsuario;

public interface IHistorialPermisoUsuarioDao extends JpaRepository<HistorialPermisoUsuario, Long> {

    List<HistorialPermisoUsuario> findByIdUsuarioOrderByFechaDescIdHistorialDesc(Long idUsuario, Pageable pagina);

    /** Último respaldo (desactivación o "sin acceso") de cada usuario pedido. */
    @Query("""
        select h from HistorialPermisoUsuario h
        where h.idUsuario in :ids and h.respaldo is not null
          and h.idHistorial = (select max(h2.idHistorial) from HistorialPermisoUsuario h2
                               where h2.idUsuario = h.idUsuario and h2.respaldo is not null)
    """)
    List<HistorialPermisoUsuario> ultimosRespaldos(@Param("ids") Collection<Long> ids);
}
