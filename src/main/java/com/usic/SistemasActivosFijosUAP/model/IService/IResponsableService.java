package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@Service
public interface IResponsableService extends IServiceGenerico<Responsable, Long>{
    Responsable buscarPorCodigo(String codigo_funcionario);
    List<Responsable> listarResponsables();
}