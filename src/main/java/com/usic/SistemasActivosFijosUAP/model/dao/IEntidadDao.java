package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

public interface IEntidadDao extends JpaRepository<Entidad, Long> {

    // @Query("SELECT e FROM Entidad e WHERE e.nombre = ?1 AND e.estado = 'ACTIVO'")
    // Entidad buscarPorNombre(String nombre);

    // @Query("SELECT e FROM Entidad e WHERE e.estado = 'ACTIVO'")
    // List<Entidad> listarEntidad();

    Optional<Entidad> findByGestionAndEntidadCodigo(Short gestion, String entidadCodigo);

    Optional<Entidad> findTopByEntidadCodigoOrderByGestionDesc(String entidadCodigo);

    @Query(value = """
              SELECT * FROM entidad
              WHERE (:q IS NULL OR desc_ent ILIKE CONCAT('%', :q, '%'))
              ORDER BY desc_ent ASC
            """, nativeQuery = true)
    List<Entidad> buscarPorQ(@Param("q") String q);

    List<Entidad> findByGestion(Short gestion);
}
