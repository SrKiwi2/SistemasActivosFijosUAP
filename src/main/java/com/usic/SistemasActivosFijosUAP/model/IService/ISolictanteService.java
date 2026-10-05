package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.Optional;

import com.usic.SistemasActivosFijosUAP.model.entity.Solicitante;

public interface ISolictanteService extends IServiceGenerico<Solicitante, Long>{

    /** Solicitante con ese nombre y cargo, sin distinguir mayúsculas. */
    Optional<Solicitante> buscarIgual(String nombre, String cargo);
}
