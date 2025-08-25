package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;

@Service
public interface ITransferenciaService extends IServiceGenerico<Transferencia, Long>{
    
    List<Transferencia> findAllConTodo();
}
