package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ActaFaltante;

/** Actas de faltantes. */
public interface IActaFaltanteDao extends JpaRepository<ActaFaltante, Long> {

    /** Lo usa la página pública de verificación (el token viaja en el QR). */
    Optional<ActaFaltante> findByToken(String token);

    @Query("select a from ActaFaltante a where a.persona.idPersona = :idPersona order by a.fechaEmision desc")
    List<ActaFaltante> deLaPersona(@Param("idPersona") Long idPersona);
}
