package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    @Query("""
           select h from HallazgoInventario h
           left join fetch h.activo
           left join fetch h.responsable r
           left join fetch r.persona
           where h.idHallazgo = :id
           """)
    Optional<HallazgoInventario> findCompleto(@Param("id") Long id);
}
