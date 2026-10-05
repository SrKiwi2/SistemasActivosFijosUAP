package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.BajaActivo;

public interface IBajaActivoDao extends JpaRepository <BajaActivo, Long>{

    /**
     * Listado del módulo: activo, responsable, su persona y su oficina en la MISMA consulta
     * (antes eran cuatro consultas por cada baja), la más reciente primero.
     */
    @Query("""
            SELECT b FROM BajaActivo b
            LEFT JOIN FETCH b.activo
            LEFT JOIN FETCH b.responsable r
            LEFT JOIN FETCH r.persona
            LEFT JOIN FETCH r.oficina
            ORDER BY b.idBajaActivo DESC
            """)
    List<BajaActivo> listarParaTabla();

    /** ¿El activo ya tiene una baja registrada? */
    boolean existsByActivoIdActivo(@Param("idActivo") Long idActivo);
}
