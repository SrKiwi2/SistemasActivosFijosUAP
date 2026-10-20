package com.usic.SistemasActivosFijosUAP.model.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.dto.duplicados.DuplicadosDTOs;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.service.control.DatosTecnicos;
import com.usic.SistemasActivosFijosUAP.model.service.duplicados.SimilitudActivos;

import lombok.RequiredArgsConstructor;

/** Lecturas del módulo Activos duplicados (solo leen). */
@Repository
@RequiredArgsConstructor
public class DuplicadosRepo {

    private final JdbcTemplate jdbc;

    private static final String SELECT_FICHA = """
        select a.id_activo, a.codigo, a.descripcion, a.costo, a.costo_anterior, a.fecha_adquisicion, a.fecha_anterior,
               a.revaluado, a._estado as estado, coalesce(a.bloqueado, false) as bloqueado, a.procedencia,
               g.id_grupo_contable, g.nombre as grupo, ax.id_auxiliar, ax.nombre as auxiliar,
               p.id_predio, p.unidad, p.descrip as predio,
               o.id_oficina, o.cod_ofi, o.nombre as oficina,
               coalesce(o.es_custodia, false) or coalesce(r.es_custodia, false) as en_custodia,
               coalesce(o.es_duplicados, false) or coalesce(r.es_duplicados, false) as en_duplicados,
               r.id_responsable, r.codigo_funcionario, pe.id_persona,
               trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as persona, pe.ci
        from activo a
        left join grupo_contable g on g.id_grupo_contable = a.id_grupo_contable
        left join auxiliar ax      on ax.id_auxiliar = a.id_auxiliar
        left join oficina o        on o.id_oficina = a.id_oficina
        left join predio p         on p.id_predio = o.id_predio
        left join responsable r    on r.id_responsable = a.id_responsable
        left join persona pe       on pe.id_persona = r.id_persona
        """;

    private static DuplicadosDTOs.Ficha ficha(ResultSet rs) throws SQLException {
        String descripcion = rs.getString("descripcion");
        DatosTecnicos dt = DatosTecnicos.de(descripcion);
        String codigo = rs.getString("codigo");
        String procedencia = rs.getString("procedencia");
        boolean soloSciaf = Activo.PROCEDENCIA_DONACION.equals(procedencia) || Activo.PROCEDENCIA_ADICIONAL.equals(procedencia)
                || Activo.esCodigoSoloSciaf(codigo);
        return new DuplicadosDTOs.Ficha(
                rs.getLong("id_activo"), codigo, descripcion, dt.marca(), dt.modelo(), dt.serie(),
                (Long) rs.getObject("id_grupo_contable", Long.class), rs.getString("grupo"),
                (Long) rs.getObject("id_auxiliar", Long.class), rs.getString("auxiliar"),
                (Double) rs.getObject("costo", Double.class), (Double) rs.getObject("costo_anterior", Double.class),
                rs.getObject("fecha_adquisicion", LocalDate.class), rs.getObject("fecha_anterior", LocalDate.class),
                (Boolean) rs.getObject("revaluado", Boolean.class),
                (Long) rs.getObject("id_predio", Long.class), rs.getString("unidad"), rs.getString("predio"),
                (Long) rs.getObject("id_oficina", Long.class), rs.getObject("cod_ofi", Short.class), rs.getString("oficina"),
                (Long) rs.getObject("id_responsable", Long.class), rs.getString("codigo_funcionario"),
                (Long) rs.getObject("id_persona", Long.class), rs.getString("persona"), rs.getString("ci"),
                rs.getString("estado"), rs.getBoolean("bloqueado"), rs.getBoolean("en_custodia"),
                rs.getBoolean("en_duplicados"), soloSciaf);
    }

    public DuplicadosDTOs.Ficha fichaPorId(Long idActivo) {
        List<DuplicadosDTOs.Ficha> l = jdbc.query(SELECT_FICHA + " where a.id_activo = ?", (rs, n) -> ficha(rs), idActivo);
        return l.isEmpty() ? null : l.get(0);
    }

    /** Por código exacto, sin distinguir mayúsculas ni espacios de los lados. */
    public DuplicadosDTOs.Ficha fichaPorCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        List<DuplicadosDTOs.Ficha> l = jdbc.query(SELECT_FICHA + " where upper(a.codigo) = upper(?)",
                (rs, n) -> ficha(rs), codigo.trim());
        return l.isEmpty() ? null : l.get(0);
    }

    // ── Posibles gemelos ─────────────────────────────────────────────────────

    /**
     * Activos vigentes del mismo predio que podrían ser el mismo bien: los del mismo grupo
     * contable y, si la descripción trae serie, los de cualquier grupo que la mencionen.
     * Se puntúan en memoria ({@link SimilitudActivos}) y vuelven los mejores.
     */
    public List<DuplicadosDTOs.Gemelo> gemelos(DuplicadosDTOs.Ficha base, int tope) {
        if (base == null || base.idPredio() == null) return List.of();
        String serie = SimilitudActivos.serieDe(base.descripcion());
        List<Object> args = new ArrayList<>();
        StringBuilder filtro = new StringBuilder("(a.id_grupo_contable = ?");
        args.add(base.idGrupo() != null ? base.idGrupo() : -1L);
        if (serie != null) {
            filtro.append(" or upper(regexp_replace(a.descripcion, '[^A-Za-z0-9]', '', 'g')) like ?");
            args.add("%" + serie + "%");
        }
        filtro.append(")");
        String sql = """
            select a.id_activo, a.codigo, a.descripcion, a.costo, a.costo_anterior, a.fecha_adquisicion, a.fecha_anterior,
                   a.revaluado, a.id_auxiliar, a.id_oficina, a.id_responsable,
                   concat_ws(' – ', o.cod_ofi, o.nombre) as oficina,
                   trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as persona
            from activo a
            join oficina o           on o.id_oficina = a.id_oficina
            left join responsable r  on r.id_responsable = a.id_responsable
            left join persona pe     on pe.id_persona = r.id_persona
            where o.id_predio = ? and a._estado = 'ACTIVO' and a.id_activo <> ?
              and not coalesce(o.es_duplicados, false) and not coalesce(r.es_duplicados, false)
              and\s""" + filtro;
        List<Object> todos = new ArrayList<>();
        todos.add(base.idPredio());
        todos.add(base.idActivo());
        todos.addAll(args);

        SimilitudActivos.Datos datosBase = datos(base.descripcion(), base.costo(), base.costoAnterior(),
                base.fechaAdquisicion(), base.fechaAnterior(), base.revaluado(), base.idAuxiliar(),
                base.idOficina(), base.idResponsable());
        List<DuplicadosDTOs.Gemelo> out = new ArrayList<>();
        jdbc.query(sql, rs -> {
            SimilitudActivos.Datos otro = datos(rs.getString("descripcion"),
                    (Double) rs.getObject("costo", Double.class), (Double) rs.getObject("costo_anterior", Double.class),
                    rs.getObject("fecha_adquisicion", LocalDate.class), rs.getObject("fecha_anterior", LocalDate.class),
                    (Boolean) rs.getObject("revaluado", Boolean.class), (Long) rs.getObject("id_auxiliar", Long.class),
                    (Long) rs.getObject("id_oficina", Long.class), (Long) rs.getObject("id_responsable", Long.class));
            SimilitudActivos.Resultado r = SimilitudActivos.comparar(datosBase, otro);
            if (r.puntaje() < SimilitudActivos.MINIMO_SUGERENCIA) return;
            out.add(new DuplicadosDTOs.Gemelo(rs.getLong("id_activo"), rs.getString("codigo"), rs.getString("descripcion"),
                    rs.getString("oficina"), rs.getString("persona"), r.puntaje(), r.motivos()));
        }, todos.toArray());
        out.sort((x, y) -> Integer.compare(y.puntaje(), x.puntaje()));
        return out.size() > tope ? out.subList(0, tope) : out;
    }

    private static SimilitudActivos.Datos datos(String descripcion, Double costo, Double costoAnterior, LocalDate fa,
                                                LocalDate fant, Boolean revaluado, Long idAux, Long idOfi, Long idResp) {
        return new SimilitudActivos.Datos(descripcion, costo, costoAnterior, fa, fant, revaluado, idAux, idOfi, idResp);
    }

    // ── Listado ──────────────────────────────────────────────────────────────

    private static final String FROM_CASOS = """
        from activo_duplicado d
        join activo a              on a.id_activo = d.id_activo
        join activo ap             on ap.id_activo = d.id_activo_principal
        left join oficina oo       on oo.id_oficina = d.id_oficina_origen
        left join predio p         on p.id_predio = oo.id_predio
        left join persona pe       on pe.id_persona = d.id_persona
        left join notificacion_duplicado n on n.id_notificacion = d.id_notificacion
        """;

    private static String filtroCasos(String q, String estado, List<Object> args) {
        StringBuilder w = new StringBuilder(" where d._estado = 'ACTIVO'");
        if (estado != null && !estado.isBlank()) {
            w.append(" and d.estado_duplicado = ?");
            args.add(estado.trim().toUpperCase());
        }
        String t = q == null ? "" : q.trim().toLowerCase();
        for (String palabra : t.split("\\s+")) {
            if (palabra.isBlank()) continue;
            w.append(" and lower(concat_ws(' ', a.codigo, ap.codigo, a.descripcion, pe.nombre, pe.paterno, pe.materno, pe.ci,"
                    + " oo.nombre, p.unidad, n.numero)) like ?");
            args.add("%" + palabra + "%");
        }
        return w.toString();
    }

    public long contar(String q, String estado) {
        List<Object> args = new ArrayList<>();
        String w = filtroCasos(q, estado, args);
        Long n = jdbc.queryForObject("select count(*) " + FROM_CASOS + w, Long.class, args.toArray());
        return n != null ? n : 0;
    }

    public List<DuplicadosDTOs.Fila> pagina(String q, String estado, int desde, int cuantos) {
        List<Object> args = new ArrayList<>();
        String w = filtroCasos(q, estado, args);
        args.add(cuantos);
        args.add(desde);
        String sql = """
            select d.id_duplicado, a.codigo, a.descripcion, ap.codigo as codigo_principal, p.unidad,
                   trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as persona,
                   concat_ws(' – ', oo.cod_ofi, oo.nombre) as oficina_origen,
                   d.estado_duplicado, d.estado_envio, d.mensaje_envio, n.numero, d._fecha_registro as fecha, d.usuario_registro
            """ + FROM_CASOS + w + " order by d.id_duplicado desc limit ? offset ?";
        return jdbc.query(sql, (rs, i) -> new DuplicadosDTOs.Fila(
                rs.getLong("id_duplicado"), rs.getString("codigo"), rs.getString("descripcion"),
                rs.getString("codigo_principal"), rs.getString("unidad"), rs.getString("persona"),
                rs.getString("oficina_origen"), rs.getString("estado_duplicado"), rs.getString("estado_envio"),
                rs.getString("mensaje_envio"), rs.getString("numero"),
                rs.getTimestamp("fecha") != null ? rs.getTimestamp("fecha").toLocalDateTime() : null,
                rs.getString("usuario_registro")), args.toArray());
    }

    /** Cuántos casos hay en cada estado. */
    public Map<String, Long> totales() {
        Map<String, Long> m = new LinkedHashMap<>();
        jdbc.query("""
            select estado_duplicado, count(*) as n from activo_duplicado where _estado = 'ACTIVO'
            group by estado_duplicado
            """, rs -> { m.put(rs.getString("estado_duplicado"), rs.getLong("n")); });
        return m;
    }

    // ── Destinos al retirar ──────────────────────────────────────────────────

    /** Responsables vigentes del predio (fuera de faltantes y duplicados); el original primero. */
    public List<DuplicadosDTOs.Destino> destinos(Long idPredio, Long idResponsableOriginal, String texto, int tope) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        String sql = """
            select r.id_responsable, r.codigo_funcionario, trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as nombre,
                   pe.ci, c.nombre as cargo, o.id_oficina, o.cod_ofi, o.nombre as oficina,
                   (r.id_responsable = ?) as original
            from responsable r
            join oficina o       on o.id_oficina = r.id_oficina and not o.es_custodia and not o.es_duplicados
                                and o._estado = 'ACTIVO' and not o.pendiente_dbf
            left join persona pe on pe.id_persona = r.id_persona
            left join cargo c    on c.id_cargo = r.id_cargo
            where o.id_predio = ? and not r.es_custodia and not r.es_duplicados and r._estado = 'ACTIVO'
              and not r.pendiente_dbf
              and (? = '' or lower(concat_ws(' ', r.codigo_funcionario, pe.nombre, pe.paterno, pe.materno, pe.ci, o.nombre,
                                              cast(o.cod_ofi as text))) like ?)
            order by original desc, nombre, o.cod_ofi
            limit ?
            """;
        return jdbc.query(sql, (rs, n) -> new DuplicadosDTOs.Destino(
                rs.getLong("id_responsable"), rs.getString("codigo_funcionario"), rs.getString("nombre"), rs.getString("ci"),
                rs.getString("cargo"), rs.getLong("id_oficina"), rs.getObject("cod_ofi", Short.class), rs.getString("oficina"),
                rs.getBoolean("original")),
                idResponsableOriginal != null ? idResponsableOriginal : -1L, idPredio, q, "%" + q + "%", tope);
    }
}
