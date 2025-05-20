package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

@Service
public interface IEntidadService extends IServiceGenerico<Entidad, Long>{
    Entidad buscarPorNombre(String nombre);
    List<Entidad> listarEntidad();
}
