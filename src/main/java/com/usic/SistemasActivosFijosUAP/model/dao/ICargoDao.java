package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;

public interface ICargoDao extends JpaRepository<Cargo, Long>{
    
    @Query("SELECT c FROM Cargo c WHERE c.nombre = ?1 AND c.estado = 'ACTIVO'")
    Cargo buscarPorNombre(String nombre);

    @Query("SELECT c FROM Cargo c WHERE c.estado = 'ACTIVO'")
    List<Cargo> listarCargos();

    Optional<Cargo> findFirstByNombreIgnoreCase(String nombre);

    Optional<Cargo> findByNombreIgnoreCase(String nombre);
}