package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.entity.Nacionalidad;

public interface INacionalidadService extends IServiceGenerico<Nacionalidad, Long>{
    
    List<Nacionalidad> listarNacionalidad();
    
    Nacionalidad buscarNacionalidadPorNombre(String nombre);
}
