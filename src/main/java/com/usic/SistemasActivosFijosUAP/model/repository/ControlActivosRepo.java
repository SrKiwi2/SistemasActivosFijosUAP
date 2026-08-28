package com.usic.SistemasActivosFijosUAP.model.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.dto.control.ActivoResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.FaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TileOficinaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TilePredioDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TileResponsableDTO;

import lombok.RequiredArgsConstructor;

/**
 * Agregaciones del mapa y de la vista Faltantes, en SQL nativo.
 *
 * <p>Van fuera de JPA a propósito: cada tile necesita media docena de conteos
 * sobre tablas distintas, y expresarlos en JPQL obliga a encadenar joins que
 * multiplican filas entre sí y devuelven totales inflados. Con subconsultas
 * correlacionadas cada número se calcula por separado y el resultado es el
 * correcto sin post-proceso en memoria.
 */
@Repository
@RequiredArgsConstructor
public class ControlActivosRepo {

    private final JdbcTemplate jdbc;

    private static final String ACTIVO   = "ACTIVO";
    private static final String ABIERTO  = "ABIERTO";
    private static final String FALTANTE = "FALTANTE";

    // ── Nivel 1: predios ─────────────────────────────────────────────────────

    private static final RowMapper<TilePredioDTO> MAPPER_PREDIO = (rs, n) -> new TilePredioDTO(
            rs.getLong("id_predio"),
            rs.getString("descrip"),
            rs.getString("unidad"),
            rs.getString("ciudad"),
            rs.getLong("oficinas"),
            rs.getLong("responsables"),
            rs.getLong("activos"),
            rs.getLong("faltantes"),
            rs.getLong("en_curso"),
            rs.getLong("levantamientos"));

    public List<TilePredioDTO> tilesPredio() {
        String sql = """
            select p.id_predio, p.descrip, p.unidad, p.ciudad,
              (select count(*) from oficina o
                 where o.id_predio = p.id_predio and o._estado = ?)                     as oficinas,
              (select count(*) from responsable r
                 join oficina o on o.id_oficina = r.id_oficina
                 where o.id_predio = p.id_predio and r._estado = ?)                     as responsables,
              (select count(*) from activo a
                 join oficina o on o.id_oficina = a.id_oficina
                 where o.id_predio = p.id_predio and a._estado = ?)                     as activos,
              (select count(*) from hallazgo_inventario h
                 join inventario i on i.id_inventario = h.id_inventario
                 join oficina o    on o.id_oficina    = i.id_oficina
                 where o.id_predio = p.id_predio
                   and h.estado_hallazgo = ? and h.tipo_hallazgo = ?)                   as faltantes,
              (select count(*) from inventario i
                 join oficina o on o.id_oficina = i.id_oficina
                 where o.id_predio = p.id_predio and i.estado_levantamiento = ?)        as en_curso,
              (select count(*) from inventario i
                 join oficina o on o.id_oficina = i.id_oficina
                 where o.id_predio = p.id_predio)                                       as levantamientos
            from predio p
            where p._estado = ?
            order by p.descrip
            """;
        return jdbc.query(sql, MAPPER_PREDIO,
                ACTIVO, ACTIVO, ACTIVO, ABIERTO, FALTANTE, "EN_EJECUCION", ACTIVO);
    }

    // ── Nivel 2: oficinas de un predio ───────────────────────────────────────

    private static final RowMapper<TileOficinaDTO> MAPPER_OFICINA = (rs, n) -> {
        Long enCurso = rs.getObject("id_en_curso", Long.class);
        Timestamp ult = rs.getTimestamp("ultimo_levantamiento");
        return new TileOficinaDTO(
                rs.getLong("id_oficina"),
                // getObject(col, Short.class) y no un cast: para una columna int2
                // el driver de PostgreSQL devuelve Integer (SMALLINT -> Integer es
                // el mapeo de la especificación JDBC), así que castear a Short
                // falla en todas las filas, no en algunas.
                rs.getObject("cod_ofi", Short.class),
                rs.getString("nombre"),
                rs.getLong("id_predio"),
                rs.getString("predio"),
                rs.getLong("responsables"),
                rs.getLong("activos"),
                rs.getLong("faltantes"),
                rs.getLong("levantamientos"),
                enCurso,
                ult == null ? null : ult.toLocalDateTime(),
                rs.getObject("ultimo_encontrados", Integer.class),
                rs.getObject("ultimo_esperados", Integer.class));
    };

    public List<TileOficinaDTO> tilesOficina(Long idPredio) {
        String sql = """
            select o.id_oficina, o.cod_ofi, o.nombre,
                   p.id_predio, p.descrip as predio,
              (select count(*) from responsable r
                 where r.id_oficina = o.id_oficina and r._estado = ?)                   as responsables,
              (select count(*) from activo a
                 where a.id_oficina = o.id_oficina and a._estado = ?)                   as activos,
              (select count(*) from hallazgo_inventario h
                 join inventario i on i.id_inventario = h.id_inventario
                 where i.id_oficina = o.id_oficina
                   and h.estado_hallazgo = ? and h.tipo_hallazgo = ?)                   as faltantes,
              (select count(*) from inventario i
                 where i.id_oficina = o.id_oficina)                                     as levantamientos,
              (select i.id_inventario from inventario i
                 where i.id_oficina = o.id_oficina and i.estado_levantamiento = ?
                 order by i.fecha_inicio desc limit 1)                                  as id_en_curso,
              u.fecha_inicio            as ultimo_levantamiento,
              u.total_activos_encontrados as ultimo_encontrados,
              u.total_activos_esperados   as ultimo_esperados
            from oficina o
            join predio p on p.id_predio = o.id_predio
            left join lateral (
                 select i.fecha_inicio, i.total_activos_encontrados, i.total_activos_esperados
                 from inventario i
                 where i.id_oficina = o.id_oficina
                 order by i.fecha_inicio desc limit 1
            ) u on true
            where o.id_predio = ? and o._estado = ?
            order by o.cod_ofi
            """;
        return jdbc.query(sql, MAPPER_OFICINA,
                ACTIVO, ACTIVO, ABIERTO, FALTANTE, "EN_EJECUCION", idPredio, ACTIVO);
    }

    // ── Nivel 3: responsables de una oficina ─────────────────────────────────

    private static final RowMapper<TileResponsableDTO> MAPPER_RESPONSABLE = (rs, n) -> new TileResponsableDTO(
            rs.getLong("id_responsable"),
            rs.getString("codigo_funcionario"),
            rs.getString("nombre"),
            rs.getString("ci"),
            rs.getString("cargo"),
            rs.getLong("id_oficina"),
            rs.getString("oficina"),
            ACTIVO.equals(rs.getString("estado_resp")),
            rs.getLong("activos"),
            rs.getLong("faltantes"),
            rs.getLong("observados"));

    /**
     * Incluye a los responsables NO vigentes a propósito. Uno dado de baja que
     * todavía figura como custodio de activos es precisamente la inconsistencia
     * que este módulo tiene que hacer visible; filtrarlo la escondería.
     */
    public List<TileResponsableDTO> tilesResponsable(Long idOficina) {
        String sql = """
            select r.id_responsable, r.codigo_funcionario, r._estado as estado_resp,
                   trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as nombre,
                   pe.ci, c.nombre as cargo,
                   o.id_oficina, o.nombre as oficina,
              (select count(*) from activo a
                 where a.id_responsable = r.id_responsable and a._estado = ?)           as activos,
              (select count(*) from hallazgo_inventario h
                 where h.id_responsable = r.id_responsable
                   and h.estado_hallazgo = ? and h.tipo_hallazgo = ?)                   as faltantes,
              (select count(*) from hallazgo_inventario h
                 where h.id_responsable = r.id_responsable
                   and h.estado_hallazgo = ? and h.tipo_hallazgo = 'OBSERVADO')         as observados
            from responsable r
            join oficina o      on o.id_oficina = r.id_oficina
            left join persona pe on pe.id_persona = r.id_persona
            left join cargo c    on c.id_cargo    = r.id_cargo
            where r.id_oficina = ?
            order by (r._estado = ?) desc, nombre
            """;
        return jdbc.query(sql, MAPPER_RESPONSABLE,
                ACTIVO, ABIERTO, FALTANTE, ABIERTO, idOficina, ACTIVO);
    }

    // ── Nivel 4: activos de un responsable ───────────────────────────────────

    private static final RowMapper<ActivoResponsableDTO> MAPPER_ACTIVO = (rs, n) -> new ActivoResponsableDTO(
            rs.getLong("id_activo"),
            rs.getString("codigo"),
            rs.getString("descripcion"),
            rs.getString("estado_activo"),
            rs.getString("auxiliar"),
            rs.getObject("costo", Double.class),
            rs.getBoolean("faltante_abierto"),
            rs.getBoolean("observado_abierto"));

    public List<ActivoResponsableDTO> activosDeResponsable(Long idResponsable) {
        String sql = """
            select a.id_activo, a.codigo, a.descripcion, a.costo,
                   ea.nombre as estado_activo, ax.nombre as auxiliar,
                   exists (select 1 from hallazgo_inventario h
                             where h.id_activo = a.id_activo
                               and h.estado_hallazgo = ? and h.tipo_hallazgo = ?)       as faltante_abierto,
                   exists (select 1 from hallazgo_inventario h
                             where h.id_activo = a.id_activo
                               and h.estado_hallazgo = ? and h.tipo_hallazgo = 'OBSERVADO') as observado_abierto
            from activo a
            left join estado_activo ea on ea.id_estado_activo = a.id_estado_activo
            left join auxiliar ax      on ax.id_auxiliar      = a.id_auxiliar
            where a.id_responsable = ? and a._estado = ?
            order by a.codigo
            """;
        return jdbc.query(sql, MAPPER_ACTIVO,
                ABIERTO, FALTANTE, ABIERTO, idResponsable, ACTIVO);
    }

    // ── Vista Faltantes ──────────────────────────────────────────────────────

    private static final RowMapper<FaltanteDTO> MAPPER_FALTANTE = (rs, n) -> {
        Timestamp det = rs.getTimestamp("fecha_deteccion");
        Timestamp res = rs.getTimestamp("fecha_resolucion");
        return new FaltanteDTO(
                rs.getLong("id_hallazgo"),
                rs.getString("tipo_hallazgo"),
                rs.getString("estado_hallazgo"),
                rs.getObject("id_activo", Long.class),
                rs.getString("codigo"),
                rs.getString("descripcion"),
                rs.getObject("id_responsable", Long.class),
                rs.getString("responsable"),
                rs.getString("codigo_funcionario"),
                rs.getLong("id_oficina"),
                rs.getString("oficina"),
                rs.getLong("id_predio"),
                rs.getString("predio"),
                rs.getLong("id_inventario"),
                rs.getString("numero_inventario"),
                det == null ? null : det.toLocalDateTime(),
                rs.getString("descripcion_discrepancia"),
                rs.getString("tipo_resolucion"),
                rs.getString("accion_correctiva"),
                res == null ? null : res.toLocalDateTime(),
                rs.getString("usuario_revisor"));
    };

    /**
     * Hallazgos filtrados. Los parámetros nulos no filtran, así la misma consulta
     * sirve para la pantalla completa y para el panel de un responsable puntual.
     */
    public List<FaltanteDTO> faltantes(Long idPredio, Long idOficina, Long idResponsable,
                                       String tipoHallazgo, String estadoHallazgo) {
        StringBuilder sql = new StringBuilder("""
            select h.id_hallazgo, h.tipo_hallazgo, h.estado_hallazgo,
                   h.id_activo, h.descripcion_discrepancia, h.tipo_resolucion,
                   h.accion_correctiva, h.fecha_resolucion, h.usuario_revisor,
                   h._fecha_registro as fecha_deteccion,
                   coalesce(a.codigo, h.codigo_fisico) as codigo,
                   coalesce(a.descripcion, h.descripcion_fisica) as descripcion,
                   r.id_responsable, r.codigo_funcionario,
                   trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as responsable,
                   o.id_oficina, o.nombre as oficina,
                   p.id_predio, p.descrip as predio,
                   i.id_inventario, i.numero_inventario
            from hallazgo_inventario h
            join inventario i        on i.id_inventario  = h.id_inventario
            join oficina o           on o.id_oficina     = i.id_oficina
            join predio p            on p.id_predio      = o.id_predio
            left join activo a       on a.id_activo      = h.id_activo
            left join responsable r  on r.id_responsable = h.id_responsable
            left join persona pe     on pe.id_persona    = r.id_persona
            where 1 = 1
            """);

        List<Object> args = new ArrayList<>();
        if (idPredio != null)       { sql.append(" and p.id_predio = ?");      args.add(idPredio); }
        if (idOficina != null)      { sql.append(" and o.id_oficina = ?");     args.add(idOficina); }
        if (idResponsable != null)  { sql.append(" and r.id_responsable = ?"); args.add(idResponsable); }
        if (tipoHallazgo != null)   { sql.append(" and h.tipo_hallazgo = ?");  args.add(tipoHallazgo); }
        if (estadoHallazgo != null) { sql.append(" and h.estado_hallazgo = ?");args.add(estadoHallazgo); }

        sql.append(" order by (h.estado_hallazgo = 'ABIERTO') desc, responsable, codigo");

        return jdbc.query(sql.toString(), MAPPER_FALTANTE, args.toArray());
    }
}
