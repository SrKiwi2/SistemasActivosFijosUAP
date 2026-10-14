package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.usic.SistemasActivosFijosUAP.model.entity.MapaEspacial;

public interface IMapaEspacialDao extends JpaRepository<MapaEspacial, Long> {
    Optional<MapaEspacial> findByPredioIdPredio(Long idPredio);
}
