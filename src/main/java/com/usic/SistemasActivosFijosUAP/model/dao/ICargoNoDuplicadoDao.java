package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.CargoNoDuplicado;

public interface ICargoNoDuplicadoDao extends JpaRepository<CargoNoDuplicado, Long> {

    boolean existsByIdCargoAAndIdCargoB(Long idCargoA, Long idCargoB);
}
