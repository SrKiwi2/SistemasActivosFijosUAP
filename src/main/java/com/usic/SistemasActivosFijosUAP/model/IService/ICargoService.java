package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;

@Service
public interface ICargoService extends IServiceGenerico<Cargo, Long>{
    
    Cargo buscarPorNombre(String nombre);
    List<Cargo> listarCargos();
    Optional<Cargo> findFirstByNombreIgnoreCase(String nombre);
    Cargo buscarOCrearPorNombre(String nombre, Long idUsuarioRegistro);
}