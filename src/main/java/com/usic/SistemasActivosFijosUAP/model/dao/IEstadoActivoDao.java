package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;

public interface IEstadoActivoDao extends JpaRepository<EstadoActivo, Long>{
    @Query("SELECT ea FROM EstadoActivo ea WHERE ea.estado = 'ACTIVO'")
    List<EstadoActivo> listarEstadoActivo();
}
