package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.entity.HojaRuta;

@Repository
public interface IHojaRutaDao extends JpaRepository<HojaRuta, Long>{
    Optional<HojaRuta> findByCodigo(String codigo);
    
    List<HojaRuta> findByGestion(Integer gestion);
    
    List<HojaRuta> findByTipo(String tipo);
    
    @Query("SELECT hr FROM HojaRuta hr WHERE hr.solicitante.idSolicitante = :solicitanteId")
    List<HojaRuta> findBySolicitante(@Param("solicitanteId") Long solicitanteId);
    
    @Query("SELECT hr FROM HojaRuta hr WHERE hr.gestion = :gestion AND hr.tipo = :tipo")
    List<HojaRuta> findByGestionAndTipo(@Param("gestion") Integer gestion, @Param("tipo") String tipo);
    
    @Query("SELECT hr FROM HojaRuta hr WHERE hr.descripcion LIKE %:descripcion%")
    List<HojaRuta> findByDescripcionContaining(@Param("descripcion") String descripcion);

    HojaRuta findByTipoAndCodigoAndGestion(String tipo, String codigo, Integer gestion);
}
