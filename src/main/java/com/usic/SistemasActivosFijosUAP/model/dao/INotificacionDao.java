package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Comunicado;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

public interface INotificacionDao extends JpaRepository<Notificacion, Long> {

     // No leídas de un usuario
    List<Notificacion> findByUsuarioAndLeidaFalseOrderByFechaCreacionDesc(Usuario usuario);

    // Todas de un usuario (paginadas para historial)
    Page<Notificacion> findByUsuarioOrderByFechaCreacionDesc(Usuario usuario, Pageable pageable);

    // Conteo no leídas
    long countByUsuarioAndLeidaFalse(Usuario usuario);

    // Marcar todas como leídas de un usuario
    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true, n.fechaLectura = :ahora " +
           "WHERE n.usuario = :usuario AND n.leida = false")
    int marcarTodasLeidas(@Param("usuario") Usuario usuario,
                          @Param("ahora") LocalDateTime ahora);

    // Verificar si ya existe notificación para esta referencia y usuario
    // Evita duplicados si el polling corre varias veces
    boolean existsByUsuarioAndReferenciaIdAndTipo(
        Usuario usuario,
        String referenciaId,
        Notificacion.TipoNotificacion tipo
    );

    // Para limpieza: notificaciones leídas con más de N días
    @Modifying
    @Query("DELETE FROM Notificacion n WHERE n.leida = true " +
           "AND n.fechaLectura < :fecha")
    int eliminarLeidasAnterioresA(@Param("fecha") LocalDateTime fecha);

    // ── Panel de control de comunicados (acuses por destinatario) ─────────────

    // Destinatarios de un comunicado con su estado de entrega/lectura
    List<Notificacion> findByComunicadoOrderByLeidaAscFechaCreacionDesc(Comunicado comunicado);

    long countByComunicado(Comunicado comunicado);

    long countByComunicadoAndEntregadaTrue(Comunicado comunicado);

    long countByComunicadoAndLeidaTrue(Comunicado comunicado);

    // Conteos agregados (entregadas, leídas) por comunicado, evita N+1 en la tabla.
    // Cada fila: [idComunicado, entregadas, leidas]
    @Query("SELECT n.comunicado.idComunicado, " +
           "SUM(CASE WHEN n.entregada = true THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN n.leida = true THEN 1 ELSE 0 END) " +
           "FROM Notificacion n WHERE n.comunicado.idComunicado IN :ids " +
           "GROUP BY n.comunicado.idComunicado")
    List<Object[]> resumenPorComunicado(@Param("ids") List<Long> ids);

    // Marca como entregadas (recibidas por el cliente) las notificaciones de un
    // usuario que aún no lo estaban. Solo afecta a las propias del usuario.
    @Modifying
    @Query("UPDATE Notificacion n SET n.entregada = true, n.fechaEntrega = :ahora " +
           "WHERE n.usuario = :usuario AND n.entregada = false AND n.idNotificacion IN :ids")
    int marcarEntregadas(@Param("usuario") Usuario usuario,
                         @Param("ids") List<Long> ids,
                         @Param("ahora") LocalDateTime ahora);
}
