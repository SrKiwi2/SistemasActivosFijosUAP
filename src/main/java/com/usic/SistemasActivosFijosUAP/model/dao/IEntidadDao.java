package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

public interface IEntidadDao extends JpaRepository<Entidad, Long>{
    
    // @Query("SELECT e FROM Entidad e WHERE e.nombre = ?1 AND e.estado = 'ACTIVO'")
    // Entidad buscarPorNombre(String nombre);

    // @Query("SELECT e FROM Entidad e WHERE e.estado = 'ACTIVO'")
    // List<Entidad> listarEntidad();

    Optional<Entidad> findByGestionAndEntidadCodigo(Short gestion, String entidadCodigo);
    Optional<Entidad> findTopByEntidadCodigoOrderByGestionDesc(String entidadCodigo);

    @Query("SELECT e FROM Entidad e " +
           "WHERE (:q IS NULL OR LOWER(e.descripcion) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY e.descripcion ASC")
    List<Entidad> buscarPorNombreLike(@Param("q") String q);
}
