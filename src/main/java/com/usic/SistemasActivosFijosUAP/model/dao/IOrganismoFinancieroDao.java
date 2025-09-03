package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;

public interface IOrganismoFinancieroDao extends JpaRepository<OrganismoFinanciero, Long>{
    
    Optional<OrganismoFinanciero> findByGestionAndCodOf(Short gestion, String codOf);

    Optional<OrganismoFinanciero> findByGestionAndSiglaIgnoreCase(Short gestion, String sigla);

    Optional<OrganismoFinanciero> findFirstByCodOfOrderByGestionDesc(String codOf);
}
