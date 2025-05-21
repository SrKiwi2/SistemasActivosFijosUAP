package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IPredioDao extends JpaRepository<Predio, Long>{
    
    @Query("SELECT p FROM Predio p WHERE p.nombre = ?1 AND p.estado = 'ACTIVO'")
    Predio buscarPorNombre(String nombre);

    @Query("SELECT p FROM Predio p WHERE p.prefijo = ?1 AND p.estado = 'ACTIVO'")
    Predio buscarPorPrefijo(String prefijo);

    @Query("SELECT p FROM Predio p WHERE p.estado = 'ACTIVO'")
    List<Predio> listarPredios();
}
