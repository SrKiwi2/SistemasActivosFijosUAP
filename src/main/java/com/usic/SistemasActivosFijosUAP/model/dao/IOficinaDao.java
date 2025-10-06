package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IOficinaDao extends JpaRepository<Oficina, Long> {
    @Query("SELECT o FROM Oficina o WHERE LOWER(o.nombre) = LOWER(?1) AND o.estado = 'ACTIVO'")
    Optional<Oficina> buscarPorNombre(String nombre);

    @Query("SELECT o FROM Oficina o WHERE o.estado = 'ACTIVO'")
    List<Oficina> listarOficinas();

    @Query("SELECT o FROM Oficina o WHERE o.codOfi = ?1 AND o.estado = 'ACTIVO'")
    List<Oficina> buscarPorCodigo(Short codOfi);

    @Query("SELECT o FROM Oficina o WHERE LOWER(o.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) AND o.estado = 'ACTIVO'")
    List<Oficina> buscarPorNombreParcial(@Param("termino") String termino);

    Optional<Oficina> findByPredioAndCodOfi(Predio predio, Short codOfi);

    Optional<Oficina> findByPredioIdPredioAndNombreIgnoreCase(Long idPredio, String nombre);

    @Query("select coalesce(max(o.codOfi), 0) + 1 from Oficina o where o.predio.idPredio = :idPredio")
    Short siguienteCodigo(@Param("idPredio") Long idPredio);

    @Query("select coalesce(max(o.codOfi), 0) from Oficina o where o.predio.id = :idPredio")
    Short maxCodOfiPorPredio(@Param("idPredio") Long idPredio);

    Optional<Oficina> findByNombre(String nombre);

    /* PARA PILLAR OFICINA CON UNIDAD Y CODOFIC */

    @Query("""
            select o
            from Oficina o
            join o.predio p
            where upper(trim(p.unidad)) = upper(trim(:unidad))
                and o.codOfi = :codOfi
            """)
    Optional<Oficina> findByUnidadAndCodOfi(@Param("unidad") String unidad,
            @Param("codOfi") Short codOfi);

    @Query("""
              SELECT o FROM Oficina o
              WHERE (:q IS NULL OR
                     LOWER(o.nombre) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(o.usuario) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(o.predio.unidad) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(o.predio.entidad.entidadCodigo) LIKE LOWER(CONCAT('%',:q,'%')))
              ORDER BY o.nombre ASC
            """)
    List<Oficina> buscarPorQ(@Param("q") String q);
}