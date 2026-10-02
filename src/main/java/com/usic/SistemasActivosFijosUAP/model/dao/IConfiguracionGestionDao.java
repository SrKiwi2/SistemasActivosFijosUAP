package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;

public interface IConfiguracionGestionDao extends JpaRepository<ConfiguracionGestion, Long> {
    Optional<ConfiguracionGestion> findByGestion(Integer gestion);

    /** La más reciente de la gestión en ese estado (una gestión puede tener varias, una por prefijo). */
    Optional<ConfiguracionGestion> findFirstByGestionAndEstadoOrderByIdConfigDesc(Integer gestion, String estado);

    Optional<ConfiguracionGestion> findFirstByGestionOrderByIdConfigDesc(Integer gestion);
    ConfiguracionGestion findByPrefijoDocumento(String PrefijoDocumento);
}
