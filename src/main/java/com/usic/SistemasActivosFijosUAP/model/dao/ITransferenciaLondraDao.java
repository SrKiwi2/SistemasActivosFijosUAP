package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaLondra;

public interface ITransferenciaLondraDao extends JpaRepository<TransferenciaLondra, Long> {
    
    boolean existsByCorrT(String corrT);
    Optional<TransferenciaLondra> findByCorrT(String corrT);
    List<TransferenciaLondra> findByEstado(TransferenciaLondra.EstadoTransferencia estado);
}
