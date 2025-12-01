package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;

public interface IHojaRutaService extends IServiceGenerico<HojaRuta, Long> {
    Optional<HojaRuta> findByCodigo(String codigo);
    List<HojaRuta> findByGestion(Integer gestion);
    List<HojaRuta> findByTipo(String tipo);
    List<HojaRuta> findBySolicitante(@Param("solicitanteId") Long solicitanteId);
    List<HojaRuta> findByGestionAndTipo(@Param("gestion") Integer gestion, @Param("tipo") String tipo);
    List<HojaRuta> findByDescripcionContaining(@Param("descripcion") String descripcion);
}
