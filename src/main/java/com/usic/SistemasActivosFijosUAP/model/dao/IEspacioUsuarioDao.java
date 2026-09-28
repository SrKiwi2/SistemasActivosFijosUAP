package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.EspacioUsuario;

public interface IEspacioUsuarioDao extends JpaRepository<EspacioUsuario, Long> {

    Optional<EspacioUsuario> findByIdUsuarioAndClave(Long idUsuario, String clave);

    List<EspacioUsuario> findByIdUsuario(Long idUsuario);

    void deleteByIdUsuarioAndClave(Long idUsuario, String clave);

    /** Limpieza de borradores viejos: es estado de trabajo, no historia. */
    @Modifying
    @Query("delete from EspacioUsuario e where e.fechaActualizacion < :antes")
    int borrarAnterioresA(@Param("antes") LocalDateTime antes);
}
