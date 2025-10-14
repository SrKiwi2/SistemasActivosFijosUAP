package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.endpoint.OficinaConteo;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;

public interface IActivoDao extends JpaRepository <Activo, Long>, JpaSpecificationExecutor<Activo>{
    
    @Query("SELECT a FROM Activo a WHERE a.nombre = ?1 AND a.estado = 'ACTIVO'")
    Activo buscarPorNombre(String nombre);

    @Query("SELECT a FROM Activo a WHERE a.codigo = ?1 AND a.estado = 'ACTIVO'")
    Activo buscarPorCodigo(String codigo);

    @Query("SELECT a FROM Activo a WHERE a.estado = 'ACTIVO'")
    List<Activo> listarActivos();

    @Query("SELECT a FROM Activo a WHERE LOWER(a.nombre) LIKE LOWER(CONCAT('%', :filtro, '%')) OR LOWER(a.codigo) LIKE LOWER(CONCAT('%', :filtro, '%'))")
    Page<Activo> buscarPorNombreOCodigo(@Param("filtro") String filtro, Pageable pageable);

    List<Activo> findByResponsableIdResponsable(Long idResponsable);

    @Query("""
        select a.oficina as oficina, count(a) as total
        from Activo a
        join a.responsable r
        join r.persona p
        where p.idPersona = :personaId
        group by a.oficina
        order by count(a) desc
    """)
    List<OficinaConteo> conteoPorOficinaDePersona(@Param("personaId") Long personaId);

    @Query("""
        select coalesce(sum(a.costo),0)
        from Activo a
        join a.responsable r
        join r.persona p
        where p.idPersona = :personaId
    """)
    Double sumaCostoPorPersona(@Param("personaId") Long personaId);

    @EntityGraph(attributePaths = {
        "oficina.nombre", "oficina.predio.nombre", "oficina.predio.municipio.nombre",
        "grupoContable.nombre", "auxiliar.nombre", "responsable.persona.nombreCompleto", "organismoFinanciero.codOf"
    })
    Optional<Activo> findByCodigo(String codigo);

    Optional<Activo> findByOficinaAndCodigo(Oficina oficina, String codigo);

        @Query("""
    SELECT a.codigo FROM Activo a
    WHERE a.codigo = :base
       OR a.codigo LIKE CONCAT(:base, '-%')
    """)
    List<String> findCodigosByBase(@Param("base") String base);
}
