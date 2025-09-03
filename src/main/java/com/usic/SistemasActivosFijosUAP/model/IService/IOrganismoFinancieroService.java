package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;

@Service
public interface IOrganismoFinancieroService extends IServiceGenerico<OrganismoFinanciero, Long>{
    
    Optional<OrganismoFinanciero> findByGestionAndCodOf(Short gestion, String codOf);

    Optional<OrganismoFinanciero> findByGestionAndSiglaIgnoreCase(Short gestion, String sigla);

    Optional<OrganismoFinanciero> findFirstByCodOfOrderByGestionDesc(String codOf);

}
