package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.IngresoDetalle;

public interface IIngresoDetalleDao extends JpaRepository<IngresoDetalle, Long>{
    
}
