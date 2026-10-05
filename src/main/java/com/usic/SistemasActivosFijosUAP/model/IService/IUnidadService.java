package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.Optional;

import com.usic.SistemasActivosFijosUAP.model.entity.Unidad;

public interface IUnidadService extends IServiceGenerico<Unidad, Long>{
    Optional<Unidad> findByNombre(String nombre);
}
