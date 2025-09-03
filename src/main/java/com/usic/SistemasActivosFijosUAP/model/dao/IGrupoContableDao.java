package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;

public interface IGrupoContableDao extends JpaRepository<GrupoContable, Long> {
    @Query("SELECT gc FROM GrupoContable gc WHERE gc.nombre = ?1 AND gc.estado = 'ACTIVO'")
    GrupoContable buscarPorNombre(String nombre);

    @Query("SELECT gc FROM GrupoContable gc WHERE gc.estado = 'ACTIVO'")
    List<GrupoContable> listarGruposContables();

    @Query("SELECT gc FROM GrupoContable gc WHERE gc.codContable = ?1 AND gc.estado = 'ACTIVO'")
    GrupoContable buscarPorCodigo(Integer codContable);

    Optional<GrupoContable> findByCodContable(Integer codContable);
    Optional<GrupoContable> findFirstByNombreIgnoreCase(String nombre);
}