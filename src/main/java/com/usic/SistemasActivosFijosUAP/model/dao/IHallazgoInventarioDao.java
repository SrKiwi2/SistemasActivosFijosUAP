package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.HallazgoInventario;

/** Faltantes, sobrantes y observaciones de condición, con su ciclo de resolución. */
public interface IHallazgoInventarioDao extends JpaRepository<HallazgoInventario, Long> {

    List<HallazgoInventario> findByInventarioIdInventario(Long idInventario);

    /**
     * Evita duplicar el hallazgo si un levantamiento se cierra dos veces (reintento
     * de la app sobre una petición que sí llegó).
     */
    Optional<HallazgoInventario> findByInventarioIdInventarioAndActivoIdActivoAndTipoHallazgo(
            Long idInventario, Long idActivo, String tipoHallazgo);

    /**
     * Un bien tiene a lo sumo un faltante pendiente (ABIERTO o EN_CUSTODIA). Si un
     * segundo levantamiento lo vuelve a extrañar, o se lo registra directo, el
     * hallazgo que ya existe es el que manda.
     */
    boolean existsByActivoIdActivoAndTipoHallazgoAndEstadoHallazgoIn(
            Long idActivo, String tipoHallazgo, List<String> estados);

    @Query("""
           select h from HallazgoInventario h
           left join fetch h.activo
           left join fetch h.responsable r
           left join fetch r.persona
           where h.idHallazgo = :id
           """)
    Optional<HallazgoInventario> findCompleto(@Param("id") Long id);

    // ── Custodia de faltantes ────────────────────────────────────────────────

    /** El faltante pendiente del bien, si lo hay (a lo sumo uno: uk_hall_faltante_pendiente). */
    @Query("""
           select h from HallazgoInventario h
           where h.activo.idActivo = :idActivo and h.tipoHallazgo = 'FALTANTE'
             and h.estadoHallazgo in ('ABIERTO', 'EN_CUSTODIA')
           """)
    List<HallazgoInventario> pendientesDelActivo(@Param("idActivo") Long idActivo);

    @Query("""
           select h from HallazgoInventario h
           join fetch h.activo a
           left join fetch h.oficinaOrigen oo
           left join fetch oo.predio
           where h.acta.idActa = :idActa
           order by a.codigo
           """)
    List<HallazgoInventario> deLaActa(@Param("idActa") Long idActa);

    /** Solo los ids: para bloquear después sin haber cargado antes las entidades (quedarían viejas). */
    @Query("select h.idHallazgo from HallazgoInventario h where h.acta.idActa = :idActa")
    List<Long> idsDeLaActa(@Param("idActa") Long idActa);

    /**
     * Los hallazgos indicados, bloqueados hasta el fin de la transacción. El despacho a la
     * custodia (cada 30 s) y la anulación de un acta tocan las mismas filas: sin el bloqueo,
     * un bien podía trasladarse mientras se anulaba su acta.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from HallazgoInventario h where h.idHallazgo in :ids order by h.idHallazgo")
    List<HallazgoInventario> bloquear(@Param("ids") Collection<Long> ids);

    /** Faltantes en un paso del traslado a la custodia (para el despacho y la confirmación). */
    @Query("""
           select h from HallazgoInventario h
           join fetch h.activo
           left join fetch h.acta
           where h.estadoEnvio in :estados and h.estadoHallazgo <> 'ANULADO'
           order by h.idHallazgo
           """)
    List<HallazgoInventario> conEnvioEn(@Param("estados") List<String> estados);
}
