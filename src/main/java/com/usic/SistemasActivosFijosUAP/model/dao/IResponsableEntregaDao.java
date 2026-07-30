package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ResponsableEntrega;

public interface IResponsableEntregaDao extends JpaRepository<ResponsableEntrega, Long> {

    @Query("SELECT r FROM ResponsableEntrega r WHERE r.estado = 'ACTIVO' ORDER BY r.nombre")
    List<ResponsableEntrega> listarActivos();

    @Query("SELECT r FROM ResponsableEntrega r WHERE r.estado = 'ACTIVO' AND "
         + "(LOWER(r.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(r.cargo) LIKE LOWER(CONCAT('%', :q, '%'))) "
         + "ORDER BY r.nombre")
    List<ResponsableEntrega> buscarPorQ(@Param("q") String q);

    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdResponsableEntregaIsNot(String nombre, Long id);

    ResponsableEntrega findBySeleccionadoTrueAndEstado(String estado);
}
