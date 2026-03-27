package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;

public interface IAsignacionActivoService extends IServiceGenerico<AsignacionActivo, Long>{

    List<AsignacionActivo> listarConDetalles();

    List<Activo> listarPendientesSinAsignacion();

    Optional<AsignacionActivo> findByActivo(@Param("activo") Activo activo);

    Optional<AsignacionActivo> findByIdConDetalles(@Param("id") Long id);

    List<AsignacionActivo> buscarConFiltros(String tipo, String estado, String buscar, String desde, String hasta);
}