package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;

public interface ICargoDao extends JpaRepository<Cargo, Long>{
    
    @Query("SELECT c FROM Cargo c WHERE c.nombre = ?1 AND c.estado = 'ACTIVO'")
    Cargo buscarPorNombre(String nombre);

    @Query("SELECT c FROM Cargo c WHERE c.estado = 'ACTIVO'")
    List<Cargo> listarCargos();

    Optional<Cargo> findFirstByNombreIgnoreCase(String nombre);

    /** Todos los cargos con ese nombre exacto, en cualquier estado (CargoServiceImpl.buscarPorNombre). */
    List<Cargo> findByNombreOrderByIdCargoAsc(String nombre);

    Optional<Cargo> findByNombreIgnoreCase(String nombre);

    @Query("SELECT c FROM Cargo c WHERE UPPER(c.nombre) LIKE UPPER(:nombre)")
    List<Cargo> buscarPorNombreLike(@Param("nombre") String nombre);

    /** Nombres de cargo para el buscador: solo los que se muestran (antes se traían todos). */
    /** Sin los FUSIONADOS (juntados en otro cargo en Personas y cargos): no se ofrecen más. */
    @Query("SELECT DISTINCT c.nombre FROM Cargo c WHERE c.nombre IS NOT NULL AND UPPER(c.nombre) LIKE :q"
            + " AND (c.estado IS NULL OR c.estado <> 'FUSIONADO') ORDER BY c.nombre")
    List<String> nombresParecidos(@Param("q") String q, Pageable pageable);
}