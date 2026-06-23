package com.usic.SistemasActivosFijosUAP.model.dao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.Comunicado;

public interface IComunicadoDao extends JpaRepository<Comunicado, Long> {

    // Listado del panel de control de envíos (más recientes primero)
    Page<Comunicado> findAllByOrderByFechaEnvioDesc(Pageable pageable);
}
