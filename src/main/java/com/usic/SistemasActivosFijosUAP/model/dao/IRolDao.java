package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.usic.SistemasActivosFijosUAP.model.entity.Rol;

public interface IRolDao extends JpaRepository <Rol, Long>{
    @Query("SELECT r FROM Rol r WHERE r.nombre = ?1 AND r.estado = 'ACTIVO'")
    Rol buscarRolPorNombre(String nombre);

    @Query("SELECT r FROM Rol r WHERE r.estado = 'ACTIVO'")
    List<Rol> listarRoles();
}
