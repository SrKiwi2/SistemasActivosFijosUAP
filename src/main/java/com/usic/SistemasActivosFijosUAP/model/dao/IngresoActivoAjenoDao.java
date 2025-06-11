package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.IngresoActivoAjeno;

public interface IngresoActivoAjenoDao extends JpaRepository <IngresoActivoAjeno, Long>{
    
}
