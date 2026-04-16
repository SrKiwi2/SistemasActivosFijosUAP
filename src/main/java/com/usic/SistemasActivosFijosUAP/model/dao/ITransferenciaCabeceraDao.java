package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.TransferenciaCabecera;

public interface ITransferenciaCabeceraDao extends JpaRepository<TransferenciaCabecera, Long> {
    boolean existsByCorrT(String corrT);

    Optional<TransferenciaCabecera> findByCorrT(String corrT);

    List<TransferenciaCabecera> findByEstado(
        TransferenciaCabecera.EstadoTransferencia estado);
}
