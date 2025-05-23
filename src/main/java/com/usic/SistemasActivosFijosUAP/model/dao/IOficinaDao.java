package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;

public interface IOficinaDao extends JpaRepository<Oficina, Long>{
    @Query("SELECT o FROM Oficina o WHERE o.nombre = ?1 AND o.estado = 'ACTIVO'")
    Oficina buscarPorNombre(String nombre);

    @Query("SELECT o FROM Oficina o WHERE o.estado = 'ACTIVO'")
    List<Oficina> listarOficinas();

    @Query("SELECT o FROM Oficina o WHERE o.codigo = ?1 AND o.estado = 'ACTIVO'")
    List<Oficina> buscarPorCodigo(String codigo);
}