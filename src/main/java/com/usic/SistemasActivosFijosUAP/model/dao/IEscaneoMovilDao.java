package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.EscaneoMovil;

public interface IEscaneoMovilDao extends JpaRepository<EscaneoMovil, Long> {

    /** Últimos escaneos de un usuario — historial reciente en la app. */
    List<EscaneoMovil> findByUsuarioIdUsuarioOrderByFechaDesc(Long idUsuario, Pageable pageable);
}
