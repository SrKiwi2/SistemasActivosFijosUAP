package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.usic.SistemasActivosFijosUAP.model.entity.MapaEspacialPublicacion;

public interface IMapaEspacialPublicacionDao extends JpaRepository<MapaEspacialPublicacion, Long> {
    List<MapaEspacialPublicacion> findByPredioIdPredioOrderByNumeroDesc(Long predioId);
    Optional<MapaEspacialPublicacion> findByPredioIdPredioAndNumero(Long predioId, Long numero);
}
