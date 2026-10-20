package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.NotificacionDuplicado;

/** Notificaciones de activos registrados como duplicados. */
public interface INotificacionDuplicadoDao extends JpaRepository<NotificacionDuplicado, Long> {

    /** Lo usa la página pública de verificación (el token viaja en el QR). */
    Optional<NotificacionDuplicado> findByToken(String token);

    /**
     * Turno para numerar: mientras dure la transacción nadie más saca un número (se libera
     * solo al confirmar o deshacer). Así no se repite el correlativo.
     */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:clave)) t", nativeQuery = true)
    long turnoNumeracion(@Param("clave") long clave);

    /** Último correlativo de la gestión: NOT-AD-<b>007</b>/2026 → 7. Serie propia, aparte de faltantes. */
    @Query(value = "select coalesce(max(cast(substring(numero from '^NOT-AD-([0-9]+)/') as integer)), 0)"
            + " from notificacion_duplicado where numero like 'NOT-AD-%/' || :gestion", nativeQuery = true)
    int ultimoCorrelativo(@Param("gestion") String gestion);
}
