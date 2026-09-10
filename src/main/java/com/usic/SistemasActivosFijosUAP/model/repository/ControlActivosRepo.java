package com.usic.SistemasActivosFijosUAP.model.repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.dto.control.ActivoResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ActivoUbicacionDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.FaltanteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.TileMunicipioDTO;
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
    private static final String EN_EJECUCION = "EN_EJECUCION";

    // ── Nivel 0: municipios ──────────────────────────────────────────────────

    private static final RowMapper<TileMunicipioDTO> MAPPER_MUNICIPIO = (rs, n) -> new TileMunicipioDTO(
            rs.getObject("id_municipio", Long.class),
            rs.getString("nombre"),
            rs.getString("codigo"),
            rs.getLong("predios"),
            rs.getLong("oficinas"),
            rs.getLong("responsables"),
            rs.getLong("activos"),
            rs.getLong("faltantes"),
            rs.getLong("en_curso"),
            rs.getLong("levantamientos"));

    /**
     * Municipios con lo que cuelga de sus predios.
     *
     * <p>Agrupa por {@code p.id_municipio} y no por la tabla municipio para que los
     * predios sin municipio cargado caigan igual en una fila (con id nulo) en vez de
     * perderse: un predio invisible en el mapa es un predio que nadie controla.
     * Por eso el join es LEFT y el group by va sobre la columna del predio.
     */
    public List<TileMunicipioDTO> tilesMunicipio() {
        String sql = """
            select p.id_municipio,
                   coalesce(max(m.nombre), 'Sin municipio asignado') as nombre,
                   max(m.codigo)                                     as codigo,
                   count(*)                                          as predios,
              coalesce(sum((select count(*) from oficina o
                 where o.id_predio = p.id_predio and o._estado = ?)), 0)              as oficinas,
              coalesce(sum((select count(*) from responsable r
                 join oficina o on o.id_oficina = r.id_oficina
                 where o.id_predio = p.id_predio and r._estado = ?)), 0)              as responsables,
              coalesce(sum((select count(*) from activo a
                 join oficina o on o.id_oficina = a.id_oficina
                 where o.id_predio = p.id_predio and a._estado = ?)), 0)              as activos,
              coalesce(sum((select count(*) from hallazgo_inventario h
                 join inventario i on i.id_inventario = h.id_inventario
                 join oficina o    on o.id_oficina    = i.id_oficina
                 where o.id_predio = p.id_predio
                   and h.estado_hallazgo = ? and h.tipo_hallazgo = ?)), 0)            as faltantes,
              coalesce(sum((select count(*) from inventario i
                 join oficina o on o.id_oficina = i.id_oficina
                 where o.id_predio = p.id_predio and i.estado_levantamiento = ?)), 0) as en_curso,
              coalesce(sum((select count(*) from inventario i
                 join oficina o on o.id_oficina = i.id_oficina
                 where o.id_predio = p.id_predio)), 0)                                as levantamientos
            from predio p
            left join municipio m on m.id_municipio = p.id_municipio
            where p._estado = ?
            group by p.id_municipio
            order by nombre
            """;
        return jdbc.query(sql, MAPPER_MUNICIPIO,
                ACTIVO, ACTIVO, ACTIVO, ABIERTO, FALTANTE, EN_EJECUCION, ACTIVO);
    }

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

    /** Todos los predios, sin importar el municipio. */
    public List<TilePredioDTO> tilesPredio() {
        return tilesPredio(null, false);
    }

    /**
     * Predios de un municipio.
     *
     * @param idMunicipio  municipio a filtrar; se ignora si {@code sinMunicipio} es true
     * @param sinMunicipio true para traer los predios que NO tienen municipio cargado
     *                     (el cuadrado "Sin municipio asignado" del nivel 0). Va como
     *                     bandera aparte porque en SQL "id_municipio = null" no filtra
     *                     nada: hay que preguntar por IS NULL explícitamente.
     */
    public List<TilePredioDTO> tilesPredio(Long idMunicipio, boolean sinMunicipio) {
        String filtro = sinMunicipio      ? "and p.id_municipio is null"
                      : idMunicipio != null ? "and p.id_municipio = ?"
                      : "";
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
            %s
            order by p.descrip
            """.formatted(filtro);

        List<Object> args = new ArrayList<>(List.of(
                ACTIVO, ACTIVO, ACTIVO, ABIERTO, FALTANTE, EN_EJECUCION, ACTIVO));
        if (!sinMunicipio && idMunicipio != null) args.add(idMunicipio);

        return jdbc.query(sql, MAPPER_PREDIO, args.toArray());
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
        return responsables("where r.id_oficina = ?", idOficina);
    }

    /** Un responsable puntual, con sus datos de cabecera para el acta. */
    public TileResponsableDTO responsable(Long idResponsable) {
        List<TileResponsableDTO> r = responsables("where r.id_responsable = ?", idResponsable);
        return r.isEmpty() ? null : r.get(0);
    }

    /**
     * Consulta compartida por los dos usos de arriba: la misma definición de "quién es
     * este responsable y cuántos bienes tiene" para la pantalla y para el acta, así el
     * papel no puede decir un número distinto del que se vio en el mapa.
     */
    private List<TileResponsableDTO> responsables(String filtro, Long id) {
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
            %s
            order by (r._estado = ?) desc, nombre
            """.formatted(filtro);
        return jdbc.query(sql, MAPPER_RESPONSABLE,
                ACTIVO, ABIERTO, FALTANTE, ABIERTO, id, ACTIVO);
    }

    // ── Buscador: activos con su ubicación ───────────────────────────────────

    /**
     * Columnas del activo con su ubicación. Se comparten entre el buscador y la lectura
     * por ids (la del informe) para que las dos vean exactamente lo mismo: si el informe
     * armara su propio SELECT, el papel podría terminar diciendo algo distinto de lo que
     * el usuario vio en pantalla al seleccionar.
     */
    private static final String SELECT_UBICACION = """
        select a.id_activo, a.codigo, a.descripcion, a.costo, a.vida_util, a.fecha_adquisicion,
               ea.nombre as estado_activo, ax.nombre as auxiliar,
               m.id_municipio, m.nombre as municipio,
               p.id_predio, p.descrip as predio,
               o.id_oficina, o.cod_ofi, o.nombre as oficina,
               r.id_responsable,
               nullif(trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)), '') as responsable,
               exists (select 1 from hallazgo_inventario h
                         where h.id_activo = a.id_activo
                           and h.estado_hallazgo = ? and h.tipo_hallazgo = ?)            as faltante_abierto,
               exists (select 1 from hallazgo_inventario h
                         where h.id_activo = a.id_activo
                           and h.estado_hallazgo = ? and h.tipo_hallazgo = 'OBSERVADO')  as observado_abierto
        """;

    /**
     * Joins del activo hacia su ubicación.
     *
     * <p>Todos LEFT a propósito: un activo sin oficina o sin responsable es justamente
     * la inconsistencia que este módulo tiene que hacer visible, no esconder.
     */
    private static final String JOINS_UBICACION = """
        from activo a
        left join oficina o        on o.id_oficina       = a.id_oficina
        left join predio p         on p.id_predio        = o.id_predio
        left join municipio m      on m.id_municipio     = p.id_municipio
        left join responsable r    on r.id_responsable   = a.id_responsable
        left join persona pe       on pe.id_persona      = r.id_persona
        left join estado_activo ea on ea.id_estado_activo = a.id_estado_activo
        left join auxiliar ax      on ax.id_auxiliar     = a.id_auxiliar
        """;

    /**
     * Filtro del buscador, compartido por la consulta de filas y la de conteo para que
     * "mostrando 200 de 852" no pueda mentir: si las dos condiciones se escribieran por
     * separado, cualquier retoque en una dejaría el número desalineado.
     */
    private static final String WHERE_BUSQUEDA = """
        where a._estado = ?
          and (lower(a.codigo) like ? or lower(a.descripcion) like ?)
        """;

    private static final String BUSQUEDA_FROM = JOINS_UBICACION + WHERE_BUSQUEDA;

    private static final RowMapper<ActivoUbicacionDTO> MAPPER_UBICACION = (rs, n) -> new ActivoUbicacionDTO(
            rs.getLong("id_activo"),
            rs.getString("codigo"),
            rs.getString("descripcion"),
            rs.getString("estado_activo"),
            rs.getString("auxiliar"),
            rs.getObject("costo", Double.class),
            rs.getBigDecimal("vida_util"),
            rs.getObject("fecha_adquisicion", java.time.LocalDate.class),
            rs.getObject("id_municipio", Long.class),
            rs.getString("municipio"),
            rs.getObject("id_predio", Long.class),
            rs.getString("predio"),
            rs.getObject("id_oficina", Long.class),
            rs.getObject("cod_ofi", Short.class),
            rs.getString("oficina"),
            rs.getObject("id_responsable", Long.class),
            rs.getString("responsable"),
            rs.getBoolean("faltante_abierto"),
            rs.getBoolean("observado_abierto"));

    /**
     * Busca activos por código o descripción y devuelve dónde está cada uno.
     *
     * <p>El orden pone primero la coincidencia exacta de código: quien pega un código
     * completo quiere ese bien, no los 40 que comparten prefijo.
     *
     * @param texto lo tecleado, ya en minúsculas y sin espacios sobrantes
     * @param tope  máximo de filas a devolver
     */
    public List<ActivoUbicacionDTO> buscarActivos(String texto, int tope) {
        String like = "%" + texto + "%";
        String sql = SELECT_UBICACION + BUSQUEDA_FROM + """
            order by (lower(a.codigo) = ?) desc, a.codigo
            limit ?
            """;
        return jdbc.query(sql, MAPPER_UBICACION,
                ABIERTO, FALTANTE, ABIERTO, ACTIVO, like, like, texto, tope);
    }

    /** Cuántos activos cumplen la misma búsqueda, para poder avisar si el resultado vino recortado. */
    public long contarBusqueda(String texto) {
        String like = "%" + texto + "%";
        Long total = jdbc.queryForObject("select count(*) " + BUSQUEDA_FROM, Long.class, ACTIVO, like, like);
        return total == null ? 0 : total;
    }

    /**
     * Los activos indicados, con su ubicación, para armar el informe de lo seleccionado.
     *
     * <p>Se releen de la base en vez de confiar en lo que manda el navegador: el papel
     * que se firma tiene que decir lo que la base dice hoy, no lo que la pantalla del
     * usuario tenía cargado hace media hora.
     */
    public List<ActivoUbicacionDTO> activosPorIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        String marcas = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = SELECT_UBICACION + JOINS_UBICACION +
                " where a.id_activo in (" + marcas + ") order by a.codigo";

        List<Object> args = new ArrayList<>(List.of(ABIERTO, FALTANTE, ABIERTO));
        args.addAll(ids);
        return jdbc.query(sql, MAPPER_UBICACION, args.toArray());
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
