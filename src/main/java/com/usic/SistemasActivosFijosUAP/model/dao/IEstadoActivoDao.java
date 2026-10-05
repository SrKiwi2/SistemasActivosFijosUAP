package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;

public interface IEstadoActivoDao extends JpaRepository<EstadoActivo, Long>{
    @Query("SELECT ea FROM EstadoActivo ea WHERE ea.estado = 'ACTIVO'")
    List<EstadoActivo> listarEstadoActivo();

    @Query("SELECT ea FROM EstadoActivo ea WHERE ea.codigo = ?1 AND ea.estado = 'ACTIVO'")
    EstadoActivo buscarPorCodigo(String codigo);

    /** Sin distinguir mayúsculas, para no repetir un código ya usado ("b" y "B"). */
    @Query("SELECT ea FROM EstadoActivo ea WHERE upper(ea.codigo) = :codigo AND ea.estado = 'ACTIVO'")
    List<EstadoActivo> listarPorCodigo(@org.springframework.data.repository.query.Param("codigo") String codigoMayus);

    /** Activos (no eliminados) por estado: [idEstadoActivo, cantidad]. Una sola consulta. */
    @Query("SELECT a.estadoActivo.idEstadoActivo, count(a) FROM Activo a "
         + "WHERE a.estadoActivo IS NOT NULL AND (a.estado IS NULL OR a.estado <> 'ELIMINADO') "
         + "GROUP BY a.estadoActivo.idEstadoActivo")
    List<Object[]> contarActivosPorEstado();
}
