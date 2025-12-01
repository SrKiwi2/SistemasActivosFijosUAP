package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;

@Repository
public interface IUnidadDao extends JpaRepository<Unidad, Long> {
    Optional<Unidad> findByNombre(String nombre);
    
    @Query("SELECT u FROM Unidad u WHERE u.nombre LIKE %:nombre%")
    List<Unidad> findByNombreContaining(@Param("nombre") String nombre);
}
