package com.usic.SistemasActivosFijosUAP.model.ServiceImpl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOrganismoFinancieroDao;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;

@Service
public class OrganismoFinancieroServiceImpl implements IOrganismoFinancieroService{

    @Autowired private IOrganismoFinancieroDao dao;
    
    @Override
    public List<OrganismoFinanciero> findAll() {
        return dao.findAll();
    }

    @Override
    public OrganismoFinanciero findById(Long idEntidad) {
        return dao.findById(idEntidad).orElse(null);
    }

    @Override
    public OrganismoFinanciero save(OrganismoFinanciero entidad) {
        return dao.save(entidad);
    }

    @Override
    public void deleteById(Long idEntidad) {
        dao.deleteById(idEntidad);
    }

    @Override
    public Optional<OrganismoFinanciero> findByGestionAndCodOf(Short gestion, String codOf) {
        return dao.findByGestionAndCodOf(gestion, codOf);
    }

    @Override
    public Optional<OrganismoFinanciero> findByGestionAndSiglaIgnoreCase(Short gestion, String sigla) {
        return dao.findByGestionAndSiglaIgnoreCase(gestion, sigla);
    }

    @Override
    public Optional<OrganismoFinanciero> findFirstByCodOfOrderByGestionDesc(String codOf) {
        return dao.findFirstByCodOfOrderByGestionDesc(codOf);
    }
    
}
