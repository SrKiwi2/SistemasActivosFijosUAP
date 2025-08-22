package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalle;

public interface ITranferenciaDetalleDao extends JpaRepository<TransferenciaDetalle, Long>{
    
}
