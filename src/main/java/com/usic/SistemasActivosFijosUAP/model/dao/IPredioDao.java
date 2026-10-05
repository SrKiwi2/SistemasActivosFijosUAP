package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IPredioDao extends JpaRepository<Predio, Long> {

    /** [idMunicipio, cantidad] de predios no eliminados por municipio: una consulta agrupada. */
    @Query("""
            SELECT p.municipio.idMunicipio, COUNT(p) FROM Predio p
            WHERE p.municipio IS NOT NULL AND (p.estado IS NULL OR p.estado <> 'ELIMINADO')
            GROUP BY p.municipio.idMunicipio
            """)
    List<Object[]> contarPorMunicipio();

    /** Todos los predios con su entidad y municipio en la misma consulta (tabla y sincronización, sin N+1). */
    @Query("SELECT p FROM Predio p JOIN FETCH p.entidad LEFT JOIN FETCH p.municipio")
    List<Predio> todosConRelaciones();

    /** Activos (no eliminados) en oficinas del predio: el prefijo de sus códigos sale del predio. */
    @Query("SELECT COUNT(a) FROM Activo a WHERE a.oficina.predio.idPredio = :idPredio AND (a.estado IS NULL OR a.estado <> 'ELIMINADO')")
    long contarActivos(@Param("idPredio") Long idPredio);

    /** Predios vigentes con ese municipio y código (MAYÚSCULAS): el par no puede repetirse. */
    @Query("""
            SELECT p FROM Predio p
            WHERE p.municipio.idMunicipio = :idMunicipio AND UPPER(TRIM(p.codigo)) = :codigo
              AND (p.estado IS NULL OR p.estado <> 'ELIMINADO')
            """)
    List<Predio> conMunicipioYCodigo(@Param("idMunicipio") Long idMunicipio, @Param("codigo") String codigo);

    /**
     * Activos ya codificados con ese prefijo ("MUN-PRED-%") que NO son de este predio: si un
     * predio se recodificó, su prefijo viejo no se puede dar a otro (los correlativos se
     * mezclarían). El prefijo llega validado (solo letras y números): sin comodines sueltos.
     */
    @Query("""
            SELECT COUNT(a) FROM Activo a LEFT JOIN a.oficina o
            WHERE a.codigo LIKE :prefijo AND (o IS NULL OR o.predio.idPredio <> :idPredio)
            """)
    long contarActivosConPrefijoDeOtro(@Param("prefijo") String prefijo, @Param("idPredio") Long idPredio);

    Optional<Predio> findByDescrip(String descrip);

    @Query("SELECT p FROM Predio p WHERE p.estado = 'ACTIVO'")
    List<Predio> listarPredios();

    Optional<Predio> findByEntidadAndUnidad(Entidad entidad, String unidad);

    Optional<Predio> findByEntidadAndUnidadIgnoreCase(Entidad entidad, String unidad);

    @Query(value = """
              SELECT p.*
              FROM predio p
              JOIN entidad e ON e.id_entidad = p.entidad_id
              WHERE (:q IS NULL OR
                     LOWER(CAST(p.descrip AS TEXT)) LIKE LOWER(CONCAT('%', :q, '%')) OR
                     LOWER(CAST(p.unidad  AS TEXT)) LIKE LOWER(CONCAT('%', :q, '%')) OR
                     LOWER(CAST(p.ciudad  AS TEXT)) LIKE LOWER(CONCAT('%', :q, '%')) OR
                     LOWER(CAST(e.entidad_codigo AS TEXT)) LIKE LOWER(CONCAT('%', :q, '%')))
              ORDER BY CAST(p.descrip AS TEXT) ASC
            """, nativeQuery = true)
    List<Predio> buscarPorQ(@Param("q") String q);

    List<Predio> findByMunicipioIdMunicipio(Long idMunicipio);

    Optional<Predio>findByUnidadIgnoreCase (String unidad);

}
