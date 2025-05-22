package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Persona;

@Service
public interface IPersonaService extends IServiceGenerico<Persona, Long>{
    
    List<Persona> listarPersonas();
    Persona buscarPersonaPorCI(String ci);
    Persona buscarPersonaPorNombrePaternoMaterno(String nombre, String paterno, String materno);
}
