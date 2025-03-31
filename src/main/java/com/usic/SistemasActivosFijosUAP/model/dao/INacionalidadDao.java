package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Nacionalidad;

public interface INacionalidadDao extends JpaRepository<Nacionalidad, Long>{
    
    @Query("SELECT n FROM Nacionalidad n WHERE n.estado = 'ACTIVO'")
    List<Nacionalidad> listarNacionalidad();

    @Query("SELECT n FROM Nacionalidad n WHERE n.nombre = ?1 AND n.estado = 'ACTIVO'")
    Nacionalidad buscarNacionalidadPorNombre(String nombre);
}
