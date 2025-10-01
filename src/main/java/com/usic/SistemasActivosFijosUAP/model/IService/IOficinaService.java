package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

@Service
public interface IOficinaService extends IServiceGenerico<Oficina, Long>{
    Optional<Oficina> buscarPorNombre(String nombre);
    List<Oficina> listarOficinas();
    List<Oficina> buscarPorCodigo(Short codOfi);
    List<Oficina> buscarPorNombreParcial(@Param("termino") String termino);
    Optional<Oficina> findByPredioAndCodOfi(Predio predio, Short codOfi);
    List<Oficina> saveAll(Iterable<Oficina> oficinas);

    short nextCodOfiForPredio(Long idPredio);
    Oficina findOrCreateConCorrelativo(String nombreOficina, Predio predio, Long idUsuario);

    Optional<Oficina> findByUnidadAndCodOfi(@Param("unidad") String unidad,
                                            @Param("codOfi") Short codOfi);

}