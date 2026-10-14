package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto;

public interface IDonacionFotoDao extends JpaRepository<DonacionFoto, Long> {

    List<DonacionFoto> findByDonacionIdDonacionAndEstadoOrderByItemAscIdFotoAsc(Long idDonacion, String estado);

    List<DonacionFoto> findByDonacionIdDonacion(Long idDonacion);

    long countByDonacionIdDonacionAndItemAndEstado(Long idDonacion, Integer item, String estado);
}
