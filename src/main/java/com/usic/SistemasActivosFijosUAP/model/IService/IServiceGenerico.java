package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
/*
 Interface genérica para los CRUD, tiene 2 parámetros, T es la entidad, y K es el tipo de dato de la clave primaria de la entidad
 */
public interface IServiceGenerico <T, K> {
    
    List<T> findAll();

    T findById(K idEntidad);

    T save(T entidad);

    void deleteById(K idEntidad);
}
