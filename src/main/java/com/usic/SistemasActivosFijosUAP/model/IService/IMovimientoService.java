package com.usic.SistemasActivosFijosUAP.model.IService;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Movimiento;

public interface IMovimientoService extends IServiceGenerico<Movimiento, Long> {
    List<Movimiento> findByHojaRuta(@Param("hojaRutaId") Long hojaRutaId);
    List<Movimiento> findByEstado(@Param("estado") String estado);
    List<Movimiento> findByFechaBetween(@Param("fechaInicio") LocalDate fechaInicio, @Param("fechaFin") LocalDate fechaFin);
    List<Movimiento> findBySolicitante(@Param("solicitanteId") Long solicitanteId);
    List<Movimiento> findByUnidad(@Param("unidadId") Long unidadId);
    List<Movimiento> findByHojaRutaAndEstado(@Param("hojaRutaId") Long hojaRutaId, @Param("estado") String estado);
}
