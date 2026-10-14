package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.MapaEspacialIconoAuxiliar;

public interface IMapaEspacialIconoAuxiliarDao extends JpaRepository<MapaEspacialIconoAuxiliar, Long> {
    interface IconoConfigurado {
        Long getAuxiliarId();
        String getIcono();
    }

    Optional<MapaEspacialIconoAuxiliar> findByAuxiliarIdAuxiliar(Long auxiliarId);
    @Query("select m.auxiliar.idAuxiliar as auxiliarId, m.icono as icono " +
            "from MapaEspacialIconoAuxiliar m where m.auxiliar.predio.idPredio = :predioId")
    List<IconoConfigurado> listarPorPredio(@Param("predioId") Long predioId);
}
