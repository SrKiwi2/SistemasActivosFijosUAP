package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

@Service
public interface IPredioServicio extends IServiceGenerico<Predio, Long>{
    Predio buscarPorNombre(String nombre);
    List<Predio> listarPredios();
    Predio buscarPorPrefijo(String prefijo);
}
