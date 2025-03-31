package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Genero;

public interface IGeneroDao extends JpaRepository<Genero, Long>{
    
    @Query("SELECT g FROM Genero g WHERE g.estado = 'ACTIVO'")
    List<Genero> listarGeneros();

    @Query("SELECT g FROM Genero g WHERE g.nombre = ?1 AND g.estado = 'ACTIVO'")
    Genero buscarGeneroPorNombre(String nombre);
}
