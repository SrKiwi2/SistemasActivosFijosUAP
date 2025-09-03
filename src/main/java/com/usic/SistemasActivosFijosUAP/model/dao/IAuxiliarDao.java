package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IAuxiliarDao extends JpaRepository<Auxiliar, Long>{
    Optional<Auxiliar> findByPredioAndCodAux(Predio predio, Short codAux);
    Optional<Auxiliar> findByPredioAndGrupoContableAndCodAux(Predio predio, GrupoContable gc, Short codAux);
    Optional<Auxiliar> findFirstByPredioAndNombreIgnoreCase(Predio predio, String nombre);
}
