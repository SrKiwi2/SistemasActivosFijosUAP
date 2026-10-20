package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ActivoDuplicado;

import jakarta.persistence.LockModeType;

/** Activos registrados como duplicados. */
public interface IActivoDuplicadoDao extends JpaRepository<ActivoDuplicado, Long> {

    /** El duplicado pendiente del activo, si lo hay (a lo sumo uno: uk_dup_activo_pendiente). */
    @Query("""
           select d from ActivoDuplicado d
           where d.activo.idActivo = :idActivo and d.estadoDuplicado in ('EN_PROCESO', 'EN_DUPLICADOS')
           """)
    List<ActivoDuplicado> pendientesDelActivo(@Param("idActivo") Long idActivo);

    /** Casos pendientes en los que el activo es el principal (el que se queda). */
    @Query("""
           select d from ActivoDuplicado d
           where d.activoPrincipal.idActivo = :idActivo and d.estadoDuplicado in ('EN_PROCESO', 'EN_DUPLICADOS')
           """)
    List<ActivoDuplicado> pendientesComoPrincipal(@Param("idActivo") Long idActivo);

    /**
     * Los casos indicados, bloqueados hasta el fin de la transacción: el ciclo de envío y el
     * retiro o la anulación tocan las mismas filas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ActivoDuplicado d where d.idDuplicado in :ids order by d.idDuplicado")
    List<ActivoDuplicado> bloquear(@Param("ids") Collection<Long> ids);

    /** Casos en un paso del traslado (despacho y confirmación). Solo los ids: para bloquear después sin haber cargado antes las entidades (quedarían viejas). */
    @Query("""
           select d.idDuplicado from ActivoDuplicado d
           where d.estadoEnvio in :estados and d.estadoDuplicado = 'EN_PROCESO'
           order by d.idDuplicado
           """)
    List<Long> idsConEnvioEn(@Param("estados") List<String> estados);

    Optional<ActivoDuplicado> findFirstByNotificacionIdNotificacion(Long idNotificacion);
}
