package com.usic.SistemasActivosFijosUAP.model.IService;

import java.io.File;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;

@Service
public interface IGrupoContableService extends IServiceGenerico<GrupoContable, Long>{
    GrupoContable buscarPorNombre(String nombre);
    List<GrupoContable> listarGruposContables();
    GrupoContable buscarPorCodigo(Integer codContable);
    void importarDesdeDBF(File archivoDBF);

    List<GrupoContable> saveAll(Iterable<GrupoContable> grupoContables);
    Optional<GrupoContable> findByCodContable(Integer codContable);
    Optional<GrupoContable> findFirstByNombreIgnoreCase(String nombre);
}