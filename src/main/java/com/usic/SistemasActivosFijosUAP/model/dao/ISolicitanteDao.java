package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;

public interface ISolicitanteDao extends JpaRepository<Solicitante, Long> {
    Optional<Solicitante> findByNombre(String nombre);
    
    List<Solicitante> findByCargo(String cargo);
    
    @Query("SELECT s FROM Solicitante s WHERE s.nombre LIKE %:nombre%")
    List<Solicitante> findByNombreContaining(@Param("nombre") String nombre);
}
