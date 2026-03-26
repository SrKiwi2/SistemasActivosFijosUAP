package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.dto.AuxOption;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IAuxiliarDao extends JpaRepository<Auxiliar, Long> {
    Optional<Auxiliar> findByPredioAndCodAux(Predio predio, Short codAux);

    Optional<Auxiliar> findByPredio_IdPredioAndGrupoContable_IdGrupoContableAndCodAux(
            Long predioId,
            Long grupoId,
            Short codAux);

    Optional<Auxiliar> findFirstByPredioAndNombreIgnoreCase(Predio predio, String nombre);

    @Query("""
            select a.idAuxiliar as id,
                   concat(a.codAux, ' - ', a.nombre) as text
            from Auxiliar a
            where a.grupoContable.idGrupoContable = :grupoId
              and (:predioId is null or a.predio.idPredio = :predioId)
              and (
                :term = '' or
                lower(a.nombre) like lower(concat('%', :term, '%')) or
                cast(a.codAux as string) like concat('%', :term, '%')
              )
            order by a.codAux, a.nombre
            """)
    Page<AuxOption> searchByGrupo(@Param("grupoId") Long grupoId,
            @Param("predioId") Long predioId,
            @Param("term") String term,
            Pageable pageable);

    @Query("""
              SELECT a FROM Auxiliar a
              WHERE (:q IS NULL OR
                     LOWER(a.nombre) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(a.usuario) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(a.predio.unidad) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(CAST(a.grupoContable.codContable AS string)) LIKE LOWER(CONCAT('%',:q,'%')) OR
                     LOWER(a.predio.entidad.entidadCodigo) LIKE LOWER(CONCAT('%',:q,'%')))
              ORDER BY a.nombre ASC
            """)
    List<Auxiliar> buscarPorQ(@Param("q") String q);

    @Query("SELECT a FROM Auxiliar a ORDER BY a.nombre ASC")
    List<Auxiliar> listarTodo();

    @Query(value = "SELECT COALESCE(MAX(a.cod_aux), 0) + 1 FROM auxiliar a WHERE a.id_predio = :idPredio AND a.id_grupo_contable = :idGrupoContable", nativeQuery = true)
    Short findNextCodAux(@Param("idPredio") Long idPredio, @Param("idGrupoContable") Long idGrupoContable);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdAuxiliarIsNot(String nombre, Long idAuxiliar);

    List<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContable(Long idPredio, Long idGrupoContable);

    Optional<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContableAndNombreIgnoreCase(Long idPredio,
            Long idGrupo, String nombre);

    @Query("SELECT MAX(a.codAux) FROM Auxiliar a WHERE a.predio.idPredio = :idPredio AND a.grupoContable.idGrupoContable = :idGrupo")
    Short findMaxCodAuxByPredioAndGrupo(@Param("idPredio") Long idPredio, @Param("idGrupo") Long idGrupo);
}