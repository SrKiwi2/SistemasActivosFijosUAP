package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;

public interface IMunicipioDao extends JpaRepository<Municipio, Long>{
    @Query("SELECT m FROM Municipio m WHERE m.nombre = ?1 AND m.estado = 'ACTIVO'")
    Municipio buscarPorNombre(String nombre);

    @Query("SELECT m FROM Municipio m WHERE m.estado = 'ACTIVO'")
    List<Municipio> listarMunicipios();
}
