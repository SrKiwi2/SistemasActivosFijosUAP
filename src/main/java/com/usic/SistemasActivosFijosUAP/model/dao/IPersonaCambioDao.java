package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio;

public interface IPersonaCambioDao extends JpaRepository<PersonaCambio, Long> {

    /** Historial de una persona (y de las unificadas en ella), lo más nuevo primero. */
    @Query("SELECT c FROM PersonaCambio c WHERE c.idPersona IN :ids ORDER BY c.idCambio DESC")
    List<PersonaCambio> dePersonas(@Param("ids") Collection<Long> ids, Pageable pageable);

    List<PersonaCambio> findByLoteOrderByIdCambioAsc(String lote);
}
