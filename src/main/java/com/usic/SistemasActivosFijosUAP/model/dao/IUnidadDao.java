package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;

@Repository
public interface IUnidadDao extends JpaRepository<Unidad, Long> {
    /** First: si alguna vez quedaron dos con el mismo nombre, no revienta (antes cortaba todo registro). */
    Optional<Unidad> findFirstByNombreOrderByIdUnidadAsc(String nombre);
}
