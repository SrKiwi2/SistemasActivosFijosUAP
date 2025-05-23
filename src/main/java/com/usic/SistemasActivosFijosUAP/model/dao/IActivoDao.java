package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

public interface IActivoDao extends JpaRepository <Activo, Long>, JpaSpecificationExecutor<Activo>{
    
    @Query("SELECT a FROM Activo a WHERE a.nombre = ?1 AND a.estado = 'ACTIVO'")
    Activo buscarPorNombre(String nombre);

    @Query("SELECT a FROM Activo a WHERE a.estado = 'ACTIVO'")
    List<Activo> listarActivos();

    @Query("SELECT a FROM Activo a WHERE LOWER(a.nombre) LIKE LOWER(CONCAT('%', :filtro, '%')) OR LOWER(a.codigo) LIKE LOWER(CONCAT('%', :filtro, '%'))")
    Page<Activo> buscarPorNombreOCodigo(@Param("filtro") String filtro, Pageable pageable);
}
