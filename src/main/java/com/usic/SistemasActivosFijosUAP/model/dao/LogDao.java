package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.LogAcceso;

public interface LogDao extends JpaRepository<LogAcceso, Long>{

    @Query("select l from LogAcceso l where lower(l.username) = lower(:username) order by l.fechaHora desc")
    List<LogAcceso> ultimosDe(@Param("username") String username, Pageable pagina);

    /** [username, max(fechaHora)] de los ingresos correctos. */
    @Query("select lower(l.username), max(l.fechaHora) from LogAcceso l where l.exito = true group by lower(l.username)")
    List<Object[]> ultimoIngresoPorUsuario();

    /** [username, count] de los intentos fallidos desde una fecha. */
    @Query("select lower(l.username), count(l) from LogAcceso l where l.exito = false and l.fechaHora >= :desde group by lower(l.username)")
    List<Object[]> fallidosDesde(@Param("desde") LocalDateTime desde);
}
