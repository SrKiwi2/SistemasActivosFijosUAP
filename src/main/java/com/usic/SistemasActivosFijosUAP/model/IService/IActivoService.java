package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;

@Service
public interface IActivoService extends IServiceGenerico<Activo, Long>{
    Activo buscarPorNombre(String nombre);
    List<Activo> listarActivos();
    Page<Activo> buscarPorNombreOCodigo(@Param("filtro") String filtro, Pageable pageable);
    Page<Activo> buscarConFiltros(String searchValue, String codigo, String responsableId,
                              String oficinaId, String fecha, Pageable pageable);

}
