package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;

@Repository
public interface IHojaRutaDao extends JpaRepository<HojaRuta, Long>{

    /**
     * Hoja por su combinación tipo + código + gestión. El código se compara sin distinguir
     * mayúsculas (se guardan en MAYÚSCULAS; las viejas pueden no estarlo) y, si quedó un
     * duplicado de antes, devuelve la primera en vez de reventar con NonUniqueResultException.
     */
    HojaRuta findFirstByTipoAndCodigoIgnoreCaseAndGestionOrderByIdHojaRutaAsc(String tipo, String codigo, Integer gestion);

    /** ¿Hay otra hoja (distinta de {@code idHojaRuta}) con esa combinación? Mira todas, no solo la primera. */
    boolean existsByTipoAndCodigoIgnoreCaseAndGestionAndIdHojaRutaNot(String tipo, String codigo, Integer gestion, Long idHojaRuta);

    boolean existsByTipoAndCodigoIgnoreCaseAndGestion(String tipo, String codigo, Integer gestion);

    /**
     * Turno para las altas del módulo (hojas, solicitantes, la unidad ACTIVOS FIJOS): mientras
     * dure la transacción nadie más registra, y se libera solo al confirmar o deshacer. Sin
     * esto, un doble clic pasaba dos veces el «¿ya existe?» y dejaba la hoja duplicada.
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:clave)) t", nativeQuery = true)
    long turnoRegistro(@Param("clave") long clave);

    /**
     * Listado con el solicitante en la misma consulta. La unidad se filtra con EXISTS: con el
     * JOIN de antes, una hoja con tres movimientos aparecía tres veces.
     */
    @Query("""
        SELECT hr FROM HojaRuta hr
        JOIN FETCH hr.solicitante
        WHERE (:gestion IS NULL OR hr.gestion = :gestion)
          AND (:unidadOrigenId IS NULL OR EXISTS (
                SELECT 1 FROM Movimiento m
                WHERE m.hojaRuta = hr AND m.unidadOrigen.idUnidad = :unidadOrigenId))
        ORDER BY hr.gestion DESC, hr.idHojaRuta DESC
        """)
    List<HojaRuta> listarParaTabla(
        @Param("gestion") Integer gestion,
        @Param("unidadOrigenId") Long unidadOrigenId
    );

    /** Gestiones que tienen hojas registradas, de la más reciente a la más antigua. */
    @Query("SELECT DISTINCT hr.gestion FROM HojaRuta hr WHERE hr.gestion IS NOT NULL ORDER BY hr.gestion DESC")
    List<Integer> gestiones();
}
