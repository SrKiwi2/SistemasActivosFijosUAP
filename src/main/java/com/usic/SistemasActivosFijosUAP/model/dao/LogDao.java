package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.LogAcceso;

public interface LogDao extends JpaRepository<LogAcceso, Long>{
    
}
