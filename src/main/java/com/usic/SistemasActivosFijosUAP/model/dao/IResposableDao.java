package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

public interface IResposableDao extends JpaRepository<Responsable, Long>{
    
    @Query("SELECT r FROM Responsable r WHERE r.codigo_funcionario = ?1 AND r.estado = 'ACTIVO'")
    Responsable buscarPorCodigo(String codigo_funcionario);

    @Query("SELECT r FROM Responsable r WHERE r.estado = 'ACTIVO'")
    List<Responsable> listarResponsables();
}
