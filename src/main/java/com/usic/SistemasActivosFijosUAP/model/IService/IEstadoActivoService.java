package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;

@Service
public interface IEstadoActivoService extends IServiceGenerico<EstadoActivo, Long>{
    List<EstadoActivo> listarEstadoActivo();
    EstadoActivo buscarPorCodigo(String codigo);
}
