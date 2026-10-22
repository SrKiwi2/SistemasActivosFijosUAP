package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.usic.SistemasActivosFijosUAP.model.entity.AuxiliarAdicional;

public interface IAuxiliarAdicionalDao extends JpaRepository<AuxiliarAdicional, Long> {
    List<AuxiliarAdicional> findByGrupoContableIdGrupoContableAndEstadoNotOrderByNombreAsc(Long grupoId, String estado);
    boolean existsByGrupoContableIdGrupoContableAndNombreIgnoreCase(Long grupoId, String nombre);
}
