package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;

@Service
public interface IOficinaService extends IServiceGenerico<Oficina, Long>{
    Oficina buscarPorNombre(String nombre);
    List<Oficina> listarOficinas();
    List<Oficina> buscarPorCodigo(String codigo);
    List<Oficina> buscarPorNombreParcial(@Param("termino") String termino);
}
