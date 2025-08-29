package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Ingreso;

@Service
public interface IngresoService extends IServiceGenerico<Ingreso, Long>{
    List<Ingreso> findAllWithTodo();
}
