package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

@Service
public interface IActivoService extends IServiceGenerico<Activo, Long>{
    Activo buscarPorNombre(String nombre);
    List<Activo> listarActivos();
}
