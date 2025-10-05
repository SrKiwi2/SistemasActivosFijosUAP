package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;

@Service
public interface IEntidadService extends IServiceGenerico<Entidad, Long>{
    // Entidad buscarPorNombre(String nombre);
    // List<Entidad> listarEntidad();
    Optional<Entidad> findByGestionAndEntidadCodigo(Short gestion, String entidadCodigo);
    Optional<Entidad> findTopByEntidadCodigoOrderByGestionDesc(String entidadCodigo);
    List<Entidad> saveAll(Iterable<Entidad> entidades);

List<Entidad> buscarPorNombreLike(@Param("q") String q);
}
