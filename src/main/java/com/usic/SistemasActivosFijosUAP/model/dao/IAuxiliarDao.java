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
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
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
              and (a.estado is null or a.estado <> 'ELIMINADO')
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

    // Nota: no hay exists...ByNombre global a propósito. La unicidad del nombre es por
    // (predio, grupo contable) — ver los exists* del final de esta interfaz.

    List<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContable(Long idPredio, Long idGrupoContable);

    @Query("""
        SELECT COALESCE(MAX(a.codAux), 0)
        FROM Auxiliar a
        WHERE a.predio.idPredio = :idPredio
          AND a.grupoContable.idGrupoContable = :idGrupo
        """)
    Integer findMaxCodAux(
        @Param("idPredio") Long idPredio,
        @Param("idGrupo")  Long idGrupo
    );
 
    Optional<Auxiliar> findByPredioIdPredioAndGrupoContableIdGrupoContableAndNombreIgnoreCase(
        Long idPredio, Long idGrupo, String nombre
    );

    Optional<Auxiliar> findByGrupoContableAndCodAux(GrupoContable grupe, Short codAux);

    @Query("""
        SELECT a FROM Auxiliar a
        JOIN a.predio p
        JOIN a.grupoContable g
        WHERE LOWER(TRIM(p.unidad)) = LOWER(TRIM(:unidad))
          AND g.codContable = :codContable
          AND a.codAux      = :codAux
          AND (a.estado IS NULL OR a.estado <> 'ELIMINADO')
        """)
    Optional<Auxiliar> findByUnidadGrupoAndCodAux(
        @Param("unidad")      String unidad,
        @Param("codContable") Integer codContable,
        @Param("codAux")      Short codAux
    );

    @Query("""
        SELECT a FROM Auxiliar a
        JOIN FETCH a.predio p
        JOIN FETCH a.grupoContable g
        WHERE LOWER(TRIM(p.unidad)) IN :unidades
          AND a.estado = 'ACTIVO'
        """)
    List<Auxiliar> findAllByUnidadesIn(@Param("unidades") List<String> unidades);

    /*
     * ── Unicidad del nombre: SIEMPRE por (predio, grupo contable) ──────────────
     * El auxiliar es un correlativo dentro de un predio y un grupo contable, no un
     * catálogo global: el mismo nombre puede (y suele) existir en varios predios con
     * codAux distinto y en distinto orden. Chequear el nombre globalmente bloqueaba
     * altas legítimas — que es lo que rompía el registro de auxiliares.
     */
    boolean existsByPredio_IdPredioAndGrupoContable_IdGrupoContableAndNombreIgnoreCase(
            Long idPredio, Long idGrupo, String nombre);

    boolean existsByPredio_IdPredioAndGrupoContable_IdGrupoContableAndNombreIgnoreCaseAndIdAuxiliarNot(
            Long idPredio, Long idGrupo, String nombre, Long idAuxiliar);

    /**
     * Auxiliares vigentes de un predio + grupo contable: los que se pueden ofrecer al
     * elegir el auxiliar de un activo. Excluye los ELIMINADO; las filas con estado nulo
     * (anteriores a que se sellara el campo) se siguen mostrando para no ocultar datos.
     */
    @Query("""
        SELECT a FROM Auxiliar a
        WHERE a.predio.idPredio = :idPredio
          AND a.grupoContable.idGrupoContable = :idGrupo
          AND (a.estado IS NULL OR a.estado <> 'ELIMINADO')
        ORDER BY a.codAux, a.nombre
        """)
    List<Auxiliar> findVigentesByPredioYGrupo(@Param("idPredio") Long idPredio,
                                              @Param("idGrupo") Long idGrupo);
}