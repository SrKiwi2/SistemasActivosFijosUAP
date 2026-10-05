package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ActaFaltante;

/** Actas de faltantes. */
public interface IActaFaltanteDao extends JpaRepository<ActaFaltante, Long> {

    /** Lo usa la página pública de verificación (el token viaja en el QR). */
    Optional<ActaFaltante> findByToken(String token);

    @Query("select a from ActaFaltante a where a.persona.idPersona = :idPersona order by a.fechaEmision desc")
    List<ActaFaltante> deLaPersona(@Param("idPersona") Long idPersona);

    /**
     * Turno para numerar notificaciones: mientras dure la transacción, nadie más saca un
     * número (se libera solo al confirmar o deshacer). Así no se repite el correlativo.
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:clave)) t", nativeQuery = true)
    long turnoNumeracion(@Param("clave") long clave);

    /** La reiterativa (no anulada) que reitera a esta notificación, si la hay. */
    Optional<ActaFaltante> findFirstByActaAnteriorIdActaAndEstadoActaNotOrderByIdActaDesc(Long idActa, String estadoActa);

    /** Último correlativo de notificación de la gestión: NOT-AF-<b>007</b>/2026 → 7. */
    @Query(value = "select coalesce(max(cast(substring(numero from '^NOT-AF-([0-9]+)/') as integer)), 0)"
            + " from acta_faltante where numero like 'NOT-AF-%/' || :gestion", nativeQuery = true)
    int ultimoCorrelativo(@Param("gestion") String gestion);
}
