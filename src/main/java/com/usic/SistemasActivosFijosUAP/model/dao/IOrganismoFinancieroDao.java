package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;

public interface IOrganismoFinancieroDao extends JpaRepository<OrganismoFinanciero, Long> {

    Optional<OrganismoFinanciero> findByGestionAndCodOf(Short gestion, String codOf);

    Optional<OrganismoFinanciero> findByGestionAndSiglaIgnoreCase(Short gestion, String sigla);

    Optional<OrganismoFinanciero> findFirstByCodOfOrderByGestionDesc(String codOf);

    @Query(value = """
              SELECT * FROM organismo_financiero ofi
              WHERE (:q IS NULL OR
                     LOWER(CAST(ofi.cod_of AS TEXT)) LIKE LOWER(CONCAT('%', :q, '%')) OR
                     LOWER(CAST(ofi.descripcion AS TEXT)) LIKE LOWER(CONCAT('%', :q, '%')) OR
                     LOWER(CAST(ofi.sigla AS TEXT)) LIKE LOWER(CONCAT('%', :q, '%')))
              ORDER BY ofi.descripcion ASC
            """, nativeQuery = true)
    List<OrganismoFinanciero> buscarPorQ(@Param("q") String q);
}
