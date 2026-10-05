package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Municipio;

@Service
public interface IMunicipioService extends IServiceGenerico<Municipio, Long>{
    Municipio buscarPorNombre(String nombre);
    List<Municipio> listarMunicipios();
    /** Activos con ese nombre o código (ya normalizados en MAYÚSCULAS). */
    List<Municipio> conNombreOCodigo(String nombre, String codigo);
    /** idMunicipio → cantidad de predios que lo usan. */
    Map<Long, Long> prediosPorMunicipio();
}
