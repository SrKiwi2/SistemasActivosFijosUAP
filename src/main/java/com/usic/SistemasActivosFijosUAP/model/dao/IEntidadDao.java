package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

public interface IEntidadDao extends JpaRepository<Entidad, Long>{
    
    // @Query("SELECT e FROM Entidad e WHERE e.nombre = ?1 AND e.estado = 'ACTIVO'")
    // Entidad buscarPorNombre(String nombre);

    // @Query("SELECT e FROM Entidad e WHERE e.estado = 'ACTIVO'")
    // List<Entidad> listarEntidad();

    Optional<Entidad> findByGestionAndEntidadCodigo(Short gestion, String entidadCodigo);
    Optional<Entidad> findTopByEntidadCodigoOrderByGestionDesc(String entidadCodigo);
}
