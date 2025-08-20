package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.Asignacion;

public interface IAsignacionDao extends JpaRepository <Asignacion, Long> {
    
}
