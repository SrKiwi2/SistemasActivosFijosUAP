package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

/**
 * Consultas de activos para la app móvil.
 *
 * <p>Repositorio aparte del {@code IActivoDao} de la web a propósito: las
 * necesidades del móvil son distintas (traer la ficha completa de una sola vez
 * y no dejar consultas colgando) y así no se toca lo que ya funciona.
 */
public interface IActivoMovilDao extends JpaRepository<Activo, Long> {

    /**
     * Ficha completa en <b>una sola consulta</b>.
     *
     * <p>El {@code join fetch} es deliberado: con conexiones lentas, resolver
     * ocho relaciones perezosas de una en una multiplica los viajes al servidor
     * y el usuario lo nota. Mejor una consulta grande que ocho pequeñas.
     */
    @Query("""
           select a from Activo a
             left join fetch a.oficina o
             left join fetch o.predio p
             left join fetch p.municipio mu
             left join fetch p.entidad e
             left join fetch a.responsable r
             left join fetch r.persona per
             left join fetch r.cargo c
             left join fetch a.grupoContable g
             left join fetch a.auxiliar aux
             left join fetch a.estadoActivo ea
             left join fetch a.organismoFinanciero of
            where a.codigo = :codigo
           """)
    Optional<Activo> fichaPorCodigo(@Param("codigo") String codigo);

    /** Igual que {@link #fichaPorCodigo} pero para un lote de códigos. */
    @Query("""
           select a from Activo a
             left join fetch a.oficina o
             left join fetch o.predio p
             left join fetch a.responsable r
             left join fetch r.persona per
             left join fetch a.grupoContable g
             left join fetch a.auxiliar aux
             left join fetch a.estadoActivo ea
            where a.codigo in :codigos
           """)
    List<Activo> fichasPorCodigos(@Param("codigos") List<String> codigos);

    /**
     * Candidatos cuando solo se tecleó el correlativo ({@code 3609}): puede
     * repetirse en distintos predios o grupos, así que se devuelven todos para
     * que el usuario elija.
     */
    @Query("""
           select a from Activo a
             left join fetch a.oficina o
             left join fetch o.predio p
             left join fetch a.responsable r
             left join fetch r.persona per
             left join fetch a.grupoContable g
            where a.codigo like %:sufijo
            order by a.codigo
           """)
    List<Activo> porCorrelativo(@Param("sufijo") String sufijo, Pageable pageable);
}
