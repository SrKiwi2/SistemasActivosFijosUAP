package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

@Service
public interface IPredioServicio extends IServiceGenerico<Predio, Long>{
    
    Optional<Predio> findByDescrip(String descrip);
    List<Predio> listarPredios();
    Optional<Predio> findByEntidadAndUnidad(Entidad entidad, String unidad);
    List<Predio> saveAll(Iterable<Predio> predios);
    Optional<Predio> findByEntidadAndUnidadIgnoreCase(Entidad entidad, String unidad);

    List<Predio> buscarPorQ(@Param("q") String q);
}
