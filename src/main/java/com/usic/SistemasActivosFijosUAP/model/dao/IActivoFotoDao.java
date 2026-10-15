package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ActivoFoto;

public interface IActivoFotoDao extends JpaRepository<ActivoFoto, Long> {

    List<ActivoFoto> findByActivoIdActivoAndEstadoOrderByIdFotoAsc(Long idActivo, String estado);

    long countByActivoIdActivoAndEstado(Long idActivo, String estado);

    /** Cuántas fotos vigentes tiene cada activo de la lista: [id_activo, cantidad]. */
    @Query("select f.activo.idActivo, count(f) from ActivoFoto f where f.activo.idActivo in :ids and f.estado = 'ACTIVO' group by f.activo.idActivo")
    List<Object[]> contarPorActivo(@Param("ids") List<Long> idsActivo);

    /** Antes de borrar un activo: sus fotos se dan de baja y se sueltan de él (FK). */
    @Modifying
    @Query(value = "update activo_foto set id_activo = null, _estado = 'ELIMINADO' where id_activo = :id", nativeQuery = true)
    int soltarDeActivo(@Param("id") Long idActivo);
}
