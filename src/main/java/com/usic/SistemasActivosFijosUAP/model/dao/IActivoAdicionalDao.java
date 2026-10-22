package com.usic.SistemasActivosFijosUAP.model.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.usic.SistemasActivosFijosUAP.model.entity.ActivoAdicional;

public interface IActivoAdicionalDao extends JpaRepository<ActivoAdicional, Long> {

    @Query("select count(x) from ActivoAdicional x where x.auxiliarAdicional.idAuxiliarAdicional = :id")
    long contarPorAuxiliarAdicional(@Param("id") Long id);

    /** Turno de las altas y cambios de código de adicionales (se libera al terminar la transacción). */
    @Query(value = "select count(*) from (select pg_advisory_xact_lock(:clave)) t", nativeQuery = true)
    long turno(@Param("clave") long clave);

    /**
     * Último correlativo de adicionales de un prefijo {@code MUN-PRED-GRP}: solo códigos
     * {@code <prefijo>-00001A}, en {@code codigo} y en {@code codigo_anulado}. El generador
     * normal y el de donaciones no los cuentan, así que las series no se tocan.
     */
    @Query(value = """
            select coalesce(max(cast(substring(substring(t.c from char_length(:prefijo) + 2) from '([0-9]{5})') as integer)), 0)
            from (select codigo as c from activo where codigo like concat(:prefijo, '-%')
                  union all
                  select codigo_anulado from activo where codigo_anulado like concat(:prefijo, '-%')) t
            where substring(t.c from char_length(:prefijo) + 2) ~ '^[0-9]{5}A$'
            """, nativeQuery = true)
    int maxCorrelativoAdicional(@Param("prefijo") String prefijo);

    Optional<ActivoAdicional> findFirstByActivoIdActivo(Long idActivo);

    List<ActivoAdicional> findByTokenRegistro(String tokenRegistro);

    /**
     * Listado (el más nuevo primero). {@code q} (en mayúsculas, con %) busca en código, código
     * antiguo, descripción, marca, modelo y serie; vacío = todos.
     */
    @Query(value = """
            select x from ActivoAdicional x
              join fetch x.activo a
              left join fetch a.grupoContable
              left join fetch a.oficina o
              left join fetch a.responsable r
              left join fetch r.persona
              left join fetch x.predio
            where a.estado <> 'ELIMINADO'
              and (:q = '' or upper(coalesce(a.codigo, '')) like :q or upper(coalesce(a.codigoAntiguo, '')) like :q
                   or upper(a.descripcion) like :q or upper(coalesce(x.marca, '')) like :q
                   or upper(coalesce(x.modelo, '')) like :q or upper(coalesce(x.serie, '')) like :q)
            order by x.idAdicional desc
            """,
           countQuery = """
            select count(x) from ActivoAdicional x join x.activo a
            where a.estado <> 'ELIMINADO'
              and (:q = '' or upper(coalesce(a.codigo, '')) like :q or upper(coalesce(a.codigoAntiguo, '')) like :q
                   or upper(a.descripcion) like :q or upper(coalesce(x.marca, '')) like :q
                   or upper(coalesce(x.modelo, '')) like :q or upper(coalesce(x.serie, '')) like :q)
            """)
    Page<ActivoAdicional> listar(@Param("q") String q, Pageable pageable);

    /** Bienes vigentes con oficina, para elegir e imprimir actas por responsable. */
    @Query("""
            select x from ActivoAdicional x join fetch x.activo a
              join fetch a.oficina o left join fetch o.predio
              left join fetch a.responsable r left join fetch r.persona left join fetch r.cargo
              left join fetch a.estadoActivo
            where a.estado = 'ACTIVO' and (:oficina is null or o.idOficina = :oficina)
              and (:responsable is null or r.idResponsable = :responsable)
            order by o.nombre, r.idResponsable, a.codigo, a.idActivo
            """)
    List<ActivoAdicional> paraReporte(@Param("oficina") Long oficina, @Param("responsable") Long responsable);

    @Query("select count(x) from ActivoAdicional x join x.activo a where a.estado <> 'ELIMINADO'")
    long contarTodos();

    /** [vigentes, con código, valor total] */
    @Query("""
            select count(x), coalesce(sum(case when a.codigo is not null then 1 else 0 end), 0), coalesce(sum(a.costo), 0)
            from ActivoAdicional x join x.activo a where a.estado = 'ACTIVO'
            """)
    List<Object[]> totales();

    @Query("""
            select x from ActivoAdicional x
              join fetch x.activo a
              left join fetch a.grupoContable
              left join fetch a.auxiliar
              left join fetch x.auxiliarAdicional
              left join fetch a.estadoActivo
              left join fetch a.organismoFinanciero
              left join fetch a.oficina o
              left join fetch o.predio
              left join fetch a.responsable r
              left join fetch r.persona
              left join fetch x.predio
            where a.idActivo = :id
            """)
    Optional<ActivoAdicional> buscarCompleto(@Param("id") Long idActivo);

    /** Varios a la vez, para las etiquetas (con lo que lleva el QR: grupo y predio). */
    @Query("""
            select x from ActivoAdicional x
              join fetch x.activo a
              left join fetch a.grupoContable
              left join fetch a.oficina o
              left join fetch o.predio
              left join fetch x.predio
            where a.idActivo in :ids
            """)
    List<ActivoAdicional> buscarVarios(@Param("ids") java.util.Collection<Long> ids);

    /** Bloquea la fila del activo mientras se edita o elimina. */
    @Query(value = "select id_activo from activo where id_activo = :id for update", nativeQuery = true)
    List<Number> bloquearActivo(@Param("id") Long idActivo);

    /**
     * ¿El activo ya tuvo vida propia? Movimientos en el historial, faltantes, asignaciones,
     * levantamientos, bloqueos, transferencias o bajas. Entonces no se borra ni cambia de código.
     */
    @Query(value = """
            select count(*) from (
              select id_activo from historial_activo where id_activo = :id and tipo_evento <> 'REGISTRO'
              union all select id_activo from hallazgo_inventario where id_activo = :id
              union all select id_activo from detalle_asignacion where id_activo = :id
              union all select id_activo from inventario_detalle where id_activo = :id
              union all select id_activo from historial_bloqueo_activo where id_activo = :id
              union all select id_activo from transferencia_detalle where id_activo = :id
              union all select id_activo from baja_activo where id_activo = :id
              union all select id_activo from asignacion_movimiento_detalle where id_activo = :id
              union all select id_activo from mantenimiento where id_activo = :id
              union all select id_activo from alerta_mantenimiento where id_activo = :id
              union all select id_activo from revaluacion where id_activo = :id) t
            """, nativeQuery = true)
    long contarVida(@Param("id") Long idActivo);

    @Modifying
    @Query(value = "delete from historial_activo where id_activo = :id and tipo_evento = 'REGISTRO'", nativeQuery = true)
    int borrarHistorialAlta(@Param("id") Long idActivo);

    /** Al asignarle código a un adicional: su alta en el historial pasa a buscarse por ese código. */
    @Modifying
    @Query(value = "update historial_activo set codigo_activo = :codigo where id_activo = :id and tipo_evento = 'REGISTRO'", nativeQuery = true)
    int recodificarHistorialAlta(@Param("id") Long idActivo, @Param("codigo") String codigo);
}
