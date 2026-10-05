package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;

import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

public interface IMovimientoService extends IServiceGenerico<Movimiento, Long> {
    /** Trayectoria de la hoja, el movimiento actual primero (con sus unidades). */
    List<Movimiento> findByHojaRuta(Long hojaRutaId);
}
