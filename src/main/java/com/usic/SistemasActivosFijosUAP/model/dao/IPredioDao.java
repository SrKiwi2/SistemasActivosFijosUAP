package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IPredioDao extends JpaRepository<Predio, Long>{
    
    @Query(value = """
    SELECT *
    FROM predio p
    WHERE p._estado = 'ACTIVO'
      AND unaccent_immutable(p.descrip) ILIKE '%' || unaccent_immutable(:q) || '%'
    ORDER BY similarity(unaccent_immutable(p.descrip), unaccent_immutable(:q)) DESC,
             strpos(LOWER(unaccent_immutable(p.descrip)), LOWER(unaccent_immutable(:q))) ASC,
             length(p.descrip) ASC
    LIMIT 1
    """, nativeQuery = true)
    Optional<Predio> buscarPorNombre(@Param("q") String descrip);

    // @Query("SELECT p FROM Predio p WHERE p.prefijo = ?1 AND p.estado = 'ACTIVO'")
    // Predio buscarPorPrefijo(String prefijo);

    @Query("SELECT p FROM Predio p WHERE p.estado = 'ACTIVO'")
    List<Predio> listarPredios();

    Optional<Predio> findByEntidadAndUnidad(Entidad entidad, String unidad);
    Optional<Predio> findByEntidadAndUnidadIgnoreCase(Entidad entidad, String unidad);
}
