package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.DonacionBien;

public interface IDonacionBienDao extends JpaRepository<DonacionBien, Long> {

    /** Bienes de un acta con su activo y lo que se muestra de él, en una consulta. */
    @Query("""
            select b from DonacionBien b
              join fetch b.activo a
              left join fetch a.grupoContable
              left join fetch a.auxiliar
              left join fetch a.estadoActivo
              left join fetch a.oficina
              left join fetch a.responsable r
              left join fetch r.persona
            where b.donacion.idDonacion = :id
            order by b.item, a.codigo
            """)
    List<DonacionBien> listarDeActa(@Param("id") Long idDonacion);

    boolean existsByDonacionIdDonacionAndItem(Long idDonacion, Integer item);

    /**
     * Unidades con «vida» después del alta: algún movimiento en el historial, un faltante
     * (de cualquier estado), un acta de asignación o un levantamiento (abierto o cerrado).
     * Esas no se borran ni cambian de código: otros registros las nombran.
     */
    @Query(value = """
            select id_activo from historial_activo where id_activo in (:ids) and tipo_evento <> 'REGISTRO'
            union select id_activo from hallazgo_inventario where id_activo in (:ids)
            union select id_activo from detalle_asignacion where id_activo in (:ids)
            union select id_activo from inventario_detalle where id_activo in (:ids)
            union select id_activo from historial_bloqueo_activo where id_activo in (:ids)
            union select id_activo from transferencia_detalle where id_activo in (:ids)
            union select id_activo from baja_activo where id_activo in (:ids)
            union select id_activo from asignacion_movimiento_detalle where id_activo in (:ids)
            union select id_activo from mantenimiento where id_activo in (:ids)
            union select id_activo from alerta_mantenimiento where id_activo in (:ids)
            union select id_activo from revaluacion where id_activo in (:ids)
            """, nativeQuery = true)
    List<Number> idsConVida(@Param("ids") List<Long> idsActivo);

    /** Al cambiar el destino de un acta sin uso: el alta en el historial pasa a decir el destino nuevo. */
    @Modifying
    @Query(value = """
            update historial_activo set id_oficina_nueva = :oficina, nombre_oficina_nueva = :nombreOficina,
                   id_resp_nuevo = :responsable, nombre_resp_nuevo = :nombreResp
            where id_activo in (:ids) and tipo_evento = 'REGISTRO'
            """, nativeQuery = true)
    int moverHistorialAlta(@Param("ids") List<Long> idsActivo, @Param("oficina") Long idOficina, @Param("nombreOficina") String nombreOficina,
                           @Param("responsable") Long idResponsable, @Param("nombreResp") String nombreResp);

    /** Al borrar unidades sin vida: su única huella en el historial es el alta. */
    @Modifying
    @Query(value = "delete from historial_activo where id_activo in (:ids) and tipo_evento = 'REGISTRO'", nativeQuery = true)
    int borrarHistorialAlta(@Param("ids") List<Long> idsActivo);

    /** Bloquea (FOR UPDATE) los activos del acta: nadie los mueve mientras se anula. */
    @Query(value = """
            select a.id_activo from activo a join donacion_bien b on b.id_activo = a.id_activo
            where b.id_donacion = :id for update of a
            """, nativeQuery = true)
    List<Number> bloquearActivosDeActa(@Param("id") Long idDonacion);

    /** Bienes que están en un levantamiento abierto (al cerrarlo, lo no visto pasa a faltante). */
    @Query(value = """
            select count(*) from inventario_detalle d join inventario i on i.id_inventario = d.id_inventario
            where d.id_activo in (:ids) and i.estado_levantamiento = 'EN_EJECUCION'
            """, nativeQuery = true)
    long contarEnLevantamientos(@Param("ids") List<Long> idsActivo);

    /** Eventos del historial distintos del alta (asignación, transferencia…) de estos bienes. */
    @Query(value = "select count(*) from historial_activo where id_activo in (:ids) and tipo_evento <> 'REGISTRO'", nativeQuery = true)
    long contarMovimientos(@Param("ids") List<Long> idsActivo);

    /** Faltantes abiertos o en custodia de estos bienes. */
    @Query(value = "select count(*) from hallazgo_inventario where id_activo in (:ids) and estado_hallazgo in ('ABIERTO', 'EN_CUSTODIA')",
           nativeQuery = true)
    long contarFaltantesPendientes(@Param("ids") List<Long> idsActivo);

    /** Bienes que figuran vigentes en un acta de asignación. */
    @Query(value = "select count(*) from detalle_asignacion where id_activo in (:ids) and coalesce(estado_detalle, 'VIGENTE') = 'VIGENTE'",
           nativeQuery = true)
    long contarEnAsignaciones(@Param("ids") List<Long> idsActivo);
}
