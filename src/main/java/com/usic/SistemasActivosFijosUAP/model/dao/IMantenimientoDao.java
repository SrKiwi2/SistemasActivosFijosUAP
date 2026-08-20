package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.Mantenimiento;

public interface IMantenimientoDao extends JpaRepository<Mantenimiento, Long> {

    List<Mantenimiento> findByActivoIdActivoOrderByFechaMantenimientoDesc(Long idActivo);
}
