package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;

public interface IUnidadService extends IServiceGenerico<Unidad, Long>{
    Optional<Unidad> findByNombre(String nombre);
    List<Unidad> findByNombreContaining(@Param("nombre") String nombre);
}
