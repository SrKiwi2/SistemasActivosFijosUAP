package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

public interface IPredioDao extends JpaRepository<Predio, Long> {

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
