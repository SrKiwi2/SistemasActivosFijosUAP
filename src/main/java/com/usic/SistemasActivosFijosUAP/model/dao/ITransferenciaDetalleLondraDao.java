package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaDetalleLondra;

public interface ITransferenciaDetalleLondraDao extends JpaRepository<TransferenciaDetalleLondra, Long>{
    List<TransferenciaDetalleLondra> findByCabeceraIdCabecera(Long idCabecera);
}
