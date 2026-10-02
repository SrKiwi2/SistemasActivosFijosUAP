package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.entity.SesionUsuario;

public interface ISesionUsuarioDao extends JpaRepository<SesionUsuario, Long> {

    @Query("select s from SesionUsuario s join fetch s.usuario u where s.serie = :serie")
    Optional<SesionUsuario> buscarPorSerie(@Param("serie") String serie);

    /**
     * Sesiones abiertas de un usuario. Las no recordadas solo cuentan si se usaron hace
     * poco: una que murió sin avisar (reinicio, corte) no figura aunque siga "ACTIVA".
     */
    @Query("select s from SesionUsuario s where s.usuario.idUsuario = :idUsuario and s.estado = 'ACTIVA'"
            + " and (s.recordar = true or s.ultimoUso > :desdeNoRecordadas) order by s.ultimoUso desc")
    List<SesionUsuario> abiertasDe(@Param("idUsuario") Long idUsuario,
            @Param("desdeNoRecordadas") LocalDateTime desdeNoRecordadas);

    /** {@code excepto}: -1 si no se exceptúa ninguna (un null tipado da problemas en PostgreSQL). */
    @Query("select s.idSesionUsuario from SesionUsuario s where s.usuario.idUsuario = :idUsuario"
            + " and s.estado = 'ACTIVA' and s.idSesionUsuario <> :excepto")
    List<Long> idsActivasDe(@Param("idUsuario") Long idUsuario, @Param("excepto") Long excepto);

    /** Solo las recordadas (cambio de contraseña sin "cerrar las demás"). */
    @Query("select s.idSesionUsuario from SesionUsuario s where s.usuario.idUsuario = :idUsuario"
            + " and s.estado = 'ACTIVA' and s.recordar = true and s.idSesionUsuario <> :excepto")
    List<Long> idsRecordadasDe(@Param("idUsuario") Long idUsuario, @Param("excepto") Long excepto);

    boolean existsByUsuarioIdUsuarioAndEquipo(Long idUsuario, String equipo);

    boolean existsByUsuarioIdUsuario(Long idUsuario);

    @Transactional
    @Modifying
    @Query("update SesionUsuario s set s.estado = 'CERRADA', s.cerradaEn = :ahora, s.motivoCierre = :motivo,"
            + " s.cerradaPor = :actor where s.idSesionUsuario in :ids and s.estado = 'ACTIVA'")
    int cerrar(@Param("ids") List<Long> ids, @Param("motivo") String motivo, @Param("actor") Long actor,
            @Param("ahora") LocalDateTime ahora);

    @Transactional
    @Modifying
    @Query("update SesionUsuario s set s.ultimoUso = :ahora, s.expira = :expira, s.idSesionHttp = :http,"
            + " s.ip = :ip, s.dispositivo = :dispositivo"
            + " where s.idSesionUsuario = :id and s.estado = 'ACTIVA'")
    int tocar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("expira") LocalDateTime expira,
            @Param("http") String idSesionHttp, @Param("ip") String ip, @Param("dispositivo") String dispositivo);

    /** Limpieza: recordadas vencidas y no recordadas abandonadas. */
    @Transactional
    @Modifying
    @Query("update SesionUsuario s set s.estado = 'CERRADA', s.cerradaEn = :ahora,"
            + " s.motivoCierre = case when s.recordar = true then 'EXPIRADA' else 'ABANDONADA' end"
            + " where s.estado = 'ACTIVA' and ((s.recordar = true and s.expira < :ahora)"
            + " or (s.recordar = false and s.ultimoUso < :desdeNoRecordadas))")
    int cerrarVencidas(@Param("ahora") LocalDateTime ahora,
            @Param("desdeNoRecordadas") LocalDateTime desdeNoRecordadas);
}
