package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.entity.Genero;

public interface IGeneroService extends IServiceGenerico<Genero, Long>{
    
    List <Genero> listarGeneros();
    Genero buscarGeneroPorNombre(String nombre);
}
