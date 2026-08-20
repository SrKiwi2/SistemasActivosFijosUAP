package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.DispositivoMovil;

public interface IDispositivoMovilDao extends JpaRepository<DispositivoMovil, Long> {

    /** Sesión viva a partir del refresh token que envía la app. */
    Optional<DispositivoMovil> findByRefreshTokenAndActivoTrue(String refreshToken);

    /** Dispositivo ya registrado por este usuario (para reutilizar la fila). */
    Optional<DispositivoMovil> findByUsuarioIdUsuarioAndDeviceId(Long idUsuario, String deviceId);

    /** Dispositivos con sesión abierta de un usuario. */
    List<DispositivoMovil> findByUsuarioIdUsuarioAndActivoTrue(Long idUsuario);

    /** Tokens FCM vivos de un conjunto de usuarios — para el push de la Fase 8. */
    @Query("""
           select d.tokenFcm
             from DispositivoMovil d
            where d.activo = true
              and d.tokenFcm is not null
              and d.usuario.idUsuario in :idsUsuario
           """)
    List<String> tokensFcmDeUsuarios(@Param("idsUsuario") List<Long> idsUsuario);
}
