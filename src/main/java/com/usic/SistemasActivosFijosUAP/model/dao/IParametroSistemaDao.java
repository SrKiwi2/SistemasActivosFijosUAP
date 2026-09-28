package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.ParametroSistema;

public interface IParametroSistemaDao extends JpaRepository<ParametroSistema, String> {
}
