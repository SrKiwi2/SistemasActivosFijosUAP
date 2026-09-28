package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.entity.HistorialBloqueoActivo;

import org.springframework.stereotype.Service;

@Service
public interface IHistorialBloqueoActivoService extends IServiceGenerico<HistorialBloqueoActivo, Long> {
    List<HistorialBloqueoActivo> findByActivoIdActivo(Long idActivo);
    List<HistorialBloqueoActivo> findByResponsableIdResponsable(Long idResponsable);
}