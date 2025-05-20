package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;

@Service
public interface IMunicipioService extends IServiceGenerico<Municipio, Long>{
    Municipio buscarPorNombre(String nombre);
    List<Municipio> listarMunicipios();
}
