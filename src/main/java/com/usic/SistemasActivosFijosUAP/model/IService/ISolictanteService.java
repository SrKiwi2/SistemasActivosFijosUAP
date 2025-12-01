package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;

public interface ISolictanteService extends IServiceGenerico<Solicitante, Long>{
    Optional<Solicitante> findByNombre(String nombre);
    
    List<Solicitante> findByCargo(String cargo);

    List<Solicitante> findByNombreContaining(@Param("nombre") String nombre);
}
