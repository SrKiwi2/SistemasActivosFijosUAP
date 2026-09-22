package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ActividadSistema;

public interface IActividadSistemaDao extends JpaRepository<ActividadSistema, Long>,
        JpaSpecificationExecutor<ActividadSistema> {

    /** Conteo por acción desde una fecha (tarjetas de resumen). Columnas: accion, total. */
    @Query("select a.accion, count(a) from ActividadSistema a where a.fecha >= :desde group by a.accion")
    List<Object[]> contarPorAccionDesde(@Param("desde") LocalDateTime desde);

    /** Usuarios más activos desde una fecha. Columnas: usuario, total. */
    @Query("select a.usuario, count(a) from ActividadSistema a where a.fecha >= :desde and a.usuario is not null "
         + "group by a.usuario order by count(a) desc")
    List<Object[]> usuariosMasActivosDesde(@Param("desde") LocalDateTime desde);

    @Query("select distinct a.usuario from ActividadSistema a where a.usuario is not null order by a.usuario")
    List<String> usuariosConActividad();
}
