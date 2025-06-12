package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.BajaActivo;

public interface IBajaActivoDao extends JpaRepository <BajaActivo, Long>{
    
}
