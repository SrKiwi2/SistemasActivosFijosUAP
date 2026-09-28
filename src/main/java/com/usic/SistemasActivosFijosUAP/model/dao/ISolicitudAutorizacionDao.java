package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.SolicitudAutorizacion;

public interface ISolicitudAutorizacionDao extends JpaRepository<SolicitudAutorizacion, Long> {

    List<SolicitudAutorizacion> findByEstadoOrderByFechaSolicitudAsc(String estado);

    List<SolicitudAutorizacion> findByEstadoNotOrderByFechaRevisionDesc(String estado, Pageable pageable);

    long countByEstado(String estado);

    boolean existsByModuloAndIdRegistroAndEstado(String modulo, Long idRegistro, String estado);

    /** Registros de un módulo con solicitud pendiente (para marcarlos en su tabla). */
    List<SolicitudAutorizacion> findByModuloAndEstadoAndIdRegistroIn(String modulo, String estado, Collection<Long> ids);
}
