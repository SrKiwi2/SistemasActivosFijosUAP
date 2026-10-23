package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ReservaCodigoAdicional;

public interface IReservaCodigoAdicionalDao extends JpaRepository<ReservaCodigoAdicional, String> {
    @Modifying
    @Query("delete from ReservaCodigoAdicional r where r.vence <= :ahora")
    int eliminarVencidas(@Param("ahora") LocalDateTime ahora);
    List<ReservaCodigoAdicional> findByPrefijoAndVenceAfterOrderByDesdeAsc(String prefijo, LocalDateTime ahora);
    @Query("select coalesce(max(r.desde + r.cantidad - 1), 0) from ReservaCodigoAdicional r "
            + "where r.prefijo = :prefijo and r.vence > :ahora")
    int maxReservado(@Param("prefijo") String prefijo, @Param("ahora") LocalDateTime ahora);
}
