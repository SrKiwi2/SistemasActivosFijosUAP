package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.DonacionFoto;

public interface IDonacionFotoDao extends JpaRepository<DonacionFoto, Long> {

    List<DonacionFoto> findByDonacionIdDonacionAndEstadoOrderByItemAscIdFotoAsc(Long idDonacion, String estado);

    List<DonacionFoto> findByDonacionIdDonacion(Long idDonacion);

    long countByDonacionIdDonacionAndItemAndEstado(Long idDonacion, Integer item, String estado);

    long countByActivoIdActivoAndEstado(Long idActivo, String estado);

    /** Fotos por ítem del formato inicial (sin activo) que siguen a la vista. */
    List<DonacionFoto> findByDonacionIdDonacionAndItemAndEstadoAndActivoIsNull(Long idDonacion, Integer item, String estado);

    List<DonacionFoto> findByActivoIdActivoAndEstado(Long idActivo, String estado);

    /** Antes de borrar activos: sus fotos se dan de baja y se sueltan del activo (FK). */
    @Modifying
    @Query(value = "update donacion_foto set id_activo = null, _estado = 'ELIMINADO' where id_activo in (:ids)", nativeQuery = true)
    int soltarDeActivos(@Param("ids") List<Long> idsActivo);
}
