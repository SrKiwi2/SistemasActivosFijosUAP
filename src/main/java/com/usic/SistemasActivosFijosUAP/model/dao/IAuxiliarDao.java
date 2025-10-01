package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.dto.AuxOption;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IAuxiliarDao extends JpaRepository<Auxiliar, Long>{
    Optional<Auxiliar> findByPredioAndCodAux(Predio predio, Short codAux);
    Optional<Auxiliar> findByPredioAndGrupoContableAndCodAux(Predio predio, GrupoContable gc, Short codAux);
    Optional<Auxiliar> findFirstByPredioAndNombreIgnoreCase(Predio predio, String nombre);

    @Query("""
        select a.idAuxiliar as id,
                concat(a.codAux, ' - ', a.nombre) as text
        from Auxiliar a
        where a.grupoContable.idGrupoContable = :grupoId
            and (
            :term = '' or
            lower(a.nombre) like lower(concat('%', :term, '%')) or
            concat('', a.codAux) like concat('%', :term, '%')
            )
        order by a.codAux, a.nombre
    """)
    Page<AuxOption> searchByGrupo(@Param("grupoId") Long grupoId,
                                  @Param("term") String term,
                                  Pageable pageable);
}
