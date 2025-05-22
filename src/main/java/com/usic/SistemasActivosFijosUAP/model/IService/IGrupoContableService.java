package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;

@Service
public interface IGrupoContableService extends IServiceGenerico<GrupoContable, Long>{
    GrupoContable buscarPorNombre(String nombre);
    List<GrupoContable> listarGruposContables();
    GrupoContable buscarPorCodigo(String codigo);
}
