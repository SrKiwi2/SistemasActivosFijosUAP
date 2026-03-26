package com.usic.SistemasActivosFijosUAP.model.IService;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.AuxOption;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

@Service
public interface IAuxiliarService extends IServiceGenerico<Auxiliar, Long> {
    List<Auxiliar> saveAll(List<Auxiliar> list);

    Optional<Auxiliar> findByPredioAndCodAux(Predio predio, Short codAux);

    Optional<Auxiliar> findByPredio_IdPredioAndGrupoContable_IdGrupoContableAndCodAux(
            Long predioId, Long grupoId, Short codAux);

    Optional<Auxiliar> findFirstByPredioAndNombreIgnoreCase(Predio predio, String nombre);

    Page<AuxOption> searchByGrupo(@Param("grupoId") Long grupoId,
            @Param("predioId") Long predioId,
            @Param("term") String term,
            Pageable pageable);

    List<Auxiliar> buscarPorQ(@Param("q") String q);

    List<Auxiliar> listarTodo();

    Short getNextCodAux(Long idPredio, Long idGrupoContable);
    boolean isNombreUnique(String nombre, Long idAuxiliar);

    List<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContable(Long idPredio, Long idGrupoContable);

    Integer findMaxCodAux(
        @Param("idPredio") Long idPredio,
        @Param("idGrupo")  Long idGrupo
    );
 
    Optional<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContableAndNombreIgnoreCase(
        Long idPredio, Long idGrupo, String nombre
    );     
}
