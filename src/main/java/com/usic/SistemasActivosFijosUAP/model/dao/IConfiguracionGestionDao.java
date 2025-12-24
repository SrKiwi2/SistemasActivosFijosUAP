package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;

public interface IConfiguracionGestionDao extends JpaRepository<ConfiguracionGestion, Long> {
    Optional<ConfiguracionGestion> findByGestion(Integer gestion);
}
