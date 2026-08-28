package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Inventario;

/** Cabeceras de levantamiento del módulo Control de Activos por Responsable. */
public interface IInventarioDao extends JpaRepository<Inventario, Long> {

    /** Idempotencia de la apertura desde el móvil. */
    Optional<Inventario> findByUuidCliente(String uuidCliente);

    /**
     * El levantamiento abierto de una oficina. Se decidió permitir uno solo a la
     * vez: si ya existe, se reabre en vez de crear otro.
     */
    Optional<Inventario> findFirstByOficinaIdOficinaAndEstadoLevantamiento(
            Long idOficina, String estadoLevantamiento);

    List<Inventario> findByIdUsuarioEjecutorOrderByFechaInicioDesc(Long idUsuarioEjecutor);

    @Query("""
           select i from Inventario i
           join fetch i.oficina o
           join fetch o.predio
           where i.idInventario = :id
           """)
    Optional<Inventario> findConUbicacion(@Param("id") Long id);

    @Query("""
           select i from Inventario i
           join fetch i.oficina o
           join fetch o.predio p
           where p.idPredio = :idPredio
           order by i.fechaInicio desc
           """)
    List<Inventario> findByPredio(@Param("idPredio") Long idPredio);
}
