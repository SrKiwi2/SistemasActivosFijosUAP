package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

public interface IResposableDao extends JpaRepository<Responsable, Long>{
    
    @Query("SELECT r FROM Responsable r WHERE r.codigoFuncionario = ?1 AND r.estado = 'ACTIVO'")
    Responsable buscarPorCodigo(String codigoFuncionario);

    @Query("SELECT r FROM Responsable r WHERE r.estado = 'ACTIVO'")
    List<Responsable> listarResponsables();

    @Query("SELECT r FROM Responsable r WHERE r.persona = ?1 AND r.oficina =?2 AND r.cargo = ?3 AND r.estado = 'ACTIVO'")
    Responsable responsablePersonaOficinaCargo(Persona persona, Oficina oficina, Cargo cargo);

    @Query("SELECT r FROM Responsable r WHERE r.persona.idPersona = :idPersona")
    List<Responsable> findAllByPersonaIdPersona(@Param("idPersona") Long idPersona);

    List<Responsable> findAllByPersona(Persona persona);

    @Query("SELECT r FROM Responsable r WHERE r.persona = ?1 AND r.estado = 'ACTIVO'")
    Responsable buscarResponsablePorPersona(Persona persona);

    Optional<Responsable> findByOficinaAndCodigoFuncionario(Oficina oficina, String codigo_funcionario);
    Optional<Responsable> findByOficinaAndPersona(Oficina oficina, Persona persona);
}