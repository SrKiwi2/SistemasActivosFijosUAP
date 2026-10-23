package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ReservaCorrelativo;

public interface IReservaCorrelativoDao extends JpaRepository<ReservaCorrelativo, String> {
    List<ReservaCorrelativo> findByTipoAndTokenOrderByIndiceAsc(String tipo, String token);
    List<ReservaCorrelativo> findByTipoAndPrefijoAndVenceAfterOrderByDesdeAsc(String tipo, String prefijo, LocalDateTime ahora);

    @Query("select coalesce(max(r.desde + r.cantidad - 1), 0) from ReservaCorrelativo r "
            + "where r.tipo = :tipo and r.prefijo = :prefijo and r.vence > :ahora")
    int maxReservado(@Param("tipo") String tipo, @Param("prefijo") String prefijo, @Param("ahora") LocalDateTime ahora);

    @Query("select count(r) from ReservaCorrelativo r where r.tipo = :tipo and r.prefijo = :prefijo "
            + "and r.vence > :ahora and r.desde <= :numero and r.desde + r.cantidad - 1 >= :numero")
    long contarOcupando(@Param("tipo") String tipo, @Param("prefijo") String prefijo,
            @Param("numero") int numero, @Param("ahora") LocalDateTime ahora);

    @Modifying
    @Query("delete from ReservaCorrelativo r where r.vence <= :ahora")
    int eliminarVencidas(@Param("ahora") LocalDateTime ahora);
}
