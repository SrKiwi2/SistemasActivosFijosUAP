package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

public interface IActivoDao extends JpaRepository <Activo, Long>{
    
    @Query("SELECT a FROM Activo a WHERE a.nombre = ?1 AND a.estado = 'ACTIVO'")
    Activo buscarPorNombre(String nombre);

    @Query("SELECT a FROM Activo a WHERE a.estado = 'ACTIVO'")
    List<Activo> listarActivos();
}
