package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;

public interface IAsignacionActivoDao extends JpaRepository<AsignacionActivo, Long>{
    
}
