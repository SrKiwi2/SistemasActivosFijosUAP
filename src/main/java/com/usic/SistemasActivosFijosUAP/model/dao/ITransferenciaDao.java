package com.usic.SistemasActivosFijosUAP.model.dao;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;

public interface ITransferenciaDao extends JpaRepository<Transferencia, Long>{
    @Query("select t from Transferencia t " +
       "left join fetch t.detalles d " +
       "left join fetch d.activo a " +
       "left join fetch d.oficinaAnterior oa " +
       "left join fetch t.responsableOrigen ro " +
       "left join fetch ro.persona " +
       "left join fetch t.responsableDestino rd " +
       "left join fetch rd.persona")
    List<Transferencia> findAllConTodo();

    /* CONFGURACION TRANSFERENCIA NUEVA */

    Optional<Transferencia> findByNumeroTransferencia(String numero);

    /**
     * Número que el trigger de la base puso al insertar. La entidad en memoria no lo tiene
     * (Hibernate no relee la fila), así que se consulta después de guardar.
     */
    @Query("select t.numeroTransferencia from Transferencia t where t.idTransferencia = :id")
    String numeroDe(@Param("id") Long id);
 
    List<Transferencia> findAllByOrderByFechaTransferenciaDesc();
 
    @Query("""
        SELECT t FROM Transferencia t
        WHERE (:tipo IS NULL OR t.tipo = :tipo)
          AND (:desde IS NULL OR t.fechaTransferencia >= :desde)
          AND (:hasta IS NULL OR t.fechaTransferencia <= :hasta)
        ORDER BY t.fechaTransferencia DESC
        """)
    List<Transferencia> buscarFiltrado(
        @Param("tipo")  String tipo,
        @Param("desde") LocalDate desde,
        @Param("hasta") LocalDate hasta
    );
}
