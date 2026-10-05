package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;

public interface ISolicitanteDao extends JpaRepository<Solicitante, Long> {

    /** Mismo nombre y cargo (sin distinguir mayúsculas): para no registrar dos veces al mismo. */
    Optional<Solicitante> findFirstByNombreIgnoreCaseAndCargoIgnoreCase(String nombre, String cargo);
}
