package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;

@Service
public interface IOrganismoFinancieroService extends IServiceGenerico<OrganismoFinanciero, Long>{
    
    Optional<OrganismoFinanciero> findByGestionAndCodOf(Short gestion, String codOf);

    Optional<OrganismoFinanciero> findByGestionAndSiglaIgnoreCase(Short gestion, String sigla);

    Optional<OrganismoFinanciero> findFirstByCodOfOrderByGestionDesc(String codOf);

    List<OrganismoFinanciero> buscarPorQ(@Param("q") String q);

    void saveAll(List<OrganismoFinanciero> batch);

}
