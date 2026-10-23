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
    @Modifying
    @Query("delete from ReservaCodigoAdicional r where r.pagina = :pagina and r.usuario = :usuario "
            + "and r.token not in :vigentes and (r.carga is null or r.carga < :carga)")
    int eliminarDeCargasAnteriores(@Param("pagina") String pagina, @Param("usuario") String usuario,
            @Param("vigentes") List<String> vigentes, @Param("carga") int carga);
    boolean existsByPaginaAndUsuarioAndCargaGreaterThan(String pagina, String usuario, Integer carga);
    /** Hora de pared de la base en una zona fija: no depende de la zona de cada servidor. */
    @Query(value = "select cast(now() at time zone 'America/La_Paz' as timestamp)", nativeQuery = true)
    java.sql.Timestamp ahora();
}
