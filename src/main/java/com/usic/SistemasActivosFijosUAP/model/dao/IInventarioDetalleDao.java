package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.InventarioDetalle;

/** La lista esperada de cada levantamiento y el resultado del recorrido. */
public interface IInventarioDetalleDao extends JpaRepository<InventarioDetalle, Long> {

    /**
     * Detalle completo para la vista y para el paquete offline. Trae responsable
     * y persona de una sola vez: son cientos de filas y resolverlas perezosamente
     * una por una haría inusable la pantalla.
     */
    @Query("""
           select d from InventarioDetalle d
           left join fetch d.responsable r
           left join fetch r.persona
           left join fetch d.estadoObservado
           where d.inventario.idInventario = :idInventario
           order by d.codigo asc
           """)
    List<InventarioDetalle> findDetalleCompleto(@Param("idInventario") Long idInventario);

    Optional<InventarioDetalle> findByInventarioIdInventarioAndActivoIdActivo(
            Long idInventario, Long idActivo);

    Optional<InventarioDetalle> findByInventarioIdInventarioAndCodigo(
            Long idInventario, String codigo);

    List<InventarioDetalle> findByInventarioIdInventarioAndSituacion(
            Long idInventario, String situacion);

    long countByInventarioIdInventario(Long idInventario);

    long countByInventarioIdInventarioAndSituacion(Long idInventario, String situacion);
}
