package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Rol;

@Service
public interface IRolService extends IServiceGenerico <Rol, Long>{
    
    Rol buscarRolPorNombre(String nombre);
    
    List<Rol> listarRoles();
}
