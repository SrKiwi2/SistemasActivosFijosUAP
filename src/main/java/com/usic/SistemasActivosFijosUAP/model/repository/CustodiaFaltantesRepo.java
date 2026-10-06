package com.usic.SistemasActivosFijosUAP.model.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.usic.SistemasActivosFijosUAP.model.dto.control.ActaResumenDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.BienPersonaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CustodiaDTOs;
import com.usic.SistemasActivosFijosUAP.model.dto.control.PersonaFaltanteDTO;

import lombok.RequiredArgsConstructor;

/**
 * Lecturas del registro de faltantes: buscar a la persona y ver todos sus bienes.
 * <p>
 * Todo va por <b>persona</b>, no por fila de {@code responsable}: en la base cada persona
 * tiene una fila por oficina (hasta decenas), y el acta de faltantes es de la persona.
 * Quedan afuera los bienes que ya están en una oficina de faltantes.
 */
@Repository
@RequiredArgsConstructor
public class CustodiaFaltantesRepo {

    private final JdbcTemplate jdbc;

    private static final String PENDIENTE = "h.tipo_hallazgo = 'FALTANTE' and h.estado_hallazgo in ('ABIERTO', 'EN_CUSTODIA')";

    private static final RowMapper<PersonaFaltanteDTO> MAPPER_PERSONA = (rs, n) -> new PersonaFaltanteDTO(
            rs.getLong("id_persona"),
            rs.getString("nombre"),
            rs.getString("ci"),
            rs.getLong("bienes"),
            rs.getLong("oficinas"),
            rs.getLong("predios"),
            rs.getLong("faltantes"),
            rs.getString("cargo_principal"),
            rs.getString("oficina_principal"));

    /**
     * Personas con bienes a cargo cuyo nombre contiene todas las palabras buscadas, o cuyo
     * CI empieza con el texto.
     */
    public List<PersonaFaltanteDTO> buscarPersonas(String texto, int tope) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        List<Object> args = new ArrayList<>();
        StringBuilder porNombre = new StringBuilder("true");
        for (String palabra : q.split("\\s+")) {
            if (palabra.isBlank()) continue;
            porNombre.append(" and lower(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) like ?");
            args.add("%" + palabra + "%");
        }
        args.add(q + "%");
        args.add(tope);

        String sql = """
            select pe.id_persona,
                   trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as nombre, pe.ci,
                   count(a.id_activo)            as bienes,
                   count(distinct a.id_oficina)  as oficinas,
                   count(distinct o.id_predio)   as predios,
                   (select count(*) from hallazgo_inventario h
                      join responsable rh on rh.id_responsable = h.id_responsable
                     where rh.id_persona = pe.id_persona and %s)   as faltantes,
                   cpp.cargo_principal,
                   opp.oficina_principal
            from persona pe
            join responsable r on r.id_persona   = pe.id_persona and not r.es_custodia
            join activo a      on a.id_responsable = r.id_responsable and a._estado = 'ACTIVO'
            join oficina o     on o.id_oficina   = a.id_oficina and not o.es_custodia
            left join lateral (
                select c.nombre as cargo_principal
                from responsable r2
                join cargo c on c.id_cargo = r2.id_cargo
                join activo a2 on a2.id_responsable = r2.id_responsable and a2._estado = 'ACTIVO'
                where r2.id_persona = pe.id_persona and not r2.es_custodia
                group by c.nombre
                order by count(*) desc
                limit 1
            ) cpp on true
            left join lateral (
                select trim(concat_ws(' ', o2.cod_ofi, '—', o2.nombre)) as oficina_principal
                from responsable r3
                join activo a3 on a3.id_responsable = r3.id_responsable and a3._estado = 'ACTIVO'
                join oficina o2 on o2.id_oficina = a3.id_oficina and not o2.es_custodia
                where r3.id_persona = pe.id_persona and not r3.es_custodia
                group by o2.id_oficina, o2.cod_ofi, o2.nombre
                order by count(*) desc
                limit 1
            ) opp on true
            where ((%s) or pe.ci like ?)
            group by pe.id_persona, pe.nombre, pe.paterno, pe.materno, pe.ci, cpp.cargo_principal, opp.oficina_principal
            order by nombre
            limit ?
            """.formatted(PENDIENTE, porNombre);
        return jdbc.query(sql, MAPPER_PERSONA, args.toArray());
    }

    private static final RowMapper<BienPersonaDTO> MAPPER_BIEN = (rs, n) -> new BienPersonaDTO(
            rs.getLong("id_activo"),
            rs.getString("codigo"),
            rs.getString("descripcion"),
            rs.getString("estado_activo"),
            rs.getLong("id_responsable"),
            rs.getString("codigo_funcionario"),
            rs.getString("cargo"),
            rs.getLong("id_oficina"),
            // getObject(col, Short.class): el driver devuelve int2 como Integer.
            rs.getObject("cod_ofi", Short.class),
            rs.getString("oficina"),
            rs.getLong("id_predio"),
            rs.getString("unidad"),
            rs.getString("predio"),
            rs.getBoolean("bloqueado"),
            rs.getObject("id_hallazgo", Long.class),
            rs.getString("estado_hallazgo"),
            rs.getString("numero_acta"),
            rs.getString("numero_inventario"));

    private static final RowMapper<ActaResumenDTO> MAPPER_ACTA = (rs, n) -> new ActaResumenDTO(
            rs.getLong("id_acta"),
            rs.getString("numero"),
            rs.getTimestamp("fecha_emision").toLocalDateTime(),
            rs.getString("usuario_emision"),
            rs.getLong("id_persona"),
            rs.getString("persona_nombre"),
            rs.getString("persona_ci"),
            rs.getInt("total_bienes"),
            rs.getString("estado_acta"),
            rs.getString("documento_respaldo"),
            rs.getLong("esperando"),
            rs.getLong("enviados"),
            rs.getLong("en_custodia"),
            rs.getLong("con_error"),
            rs.getLong("resueltos"),
            rs.getInt("numero_reiterativa"),
            rs.getLong("vinculados"),
            rs.getBoolean("reiterada"));

    /** Actas de faltantes, las más nuevas primero; de una persona si se indica. */
    public List<ActaResumenDTO> actas(Long idPersona, int tope) {
        String sql = """
            select af.id_acta, af.numero, af.fecha_emision, af.usuario_emision, af.id_persona,
                   af.persona_nombre, af.persona_ci, af.total_bienes, af.estado_acta, af.documento_respaldo,
                   count(*) filter (where h.estado_envio = 'ESPERANDO_ALTA' and h.estado_hallazgo = 'ABIERTO') as esperando,
                   count(*) filter (where h.estado_envio = 'ENVIADO' and h.estado_hallazgo = 'ABIERTO')        as enviados,
                   count(*) filter (where h.estado_hallazgo = 'EN_CUSTODIA')                                  as en_custodia,
                   count(*) filter (where h.estado_envio = 'ERROR' and h.estado_hallazgo = 'ABIERTO')          as con_error,
                   count(*) filter (where h.estado_hallazgo = 'RESUELTO')                      as resueltos,
                   coalesce(af.numero_reiterativa, 0)                                        as numero_reiterativa,
                   count(h.id_hallazgo)                                                      as vinculados,
                   exists (select 1 from acta_faltante r
                            where r.id_acta_anterior = af.id_acta and r.estado_acta <> 'ANULADA')    as reiterada
            from acta_faltante af
            left join hallazgo_inventario h on h.id_acta = af.id_acta
            where (cast(? as bigint) is null or af.id_persona = ?)
            group by af.id_acta
            order by af.fecha_emision desc
            limit ?
            """;
        return jdbc.query(sql, MAPPER_ACTA, idPersona, idPersona, tope);
    }

    // ── Resolución: a quién puede volver un bien que apareció ────────────────

    /**
     * Responsables vigentes del predio, fuera de la custodia; el original primero.
     * Mismo predio a propósito: la devolución es una transferencia interna y no toca CODAUX.
     */
    public List<CustodiaDTOs.Destino> destinos(Long idPredio, Long idResponsableOriginal, Long idOficinaOrigen,
                                               String texto, int tope) {
        String q = texto == null ? "" : texto.trim().toLowerCase();
        String sql = """
            select r.id_responsable, r.codigo_funcionario, trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as nombre,
                   pe.ci, c.nombre as cargo, o.id_oficina, o.cod_ofi, o.nombre as oficina,
                   (r.id_responsable = ?) as original, (o.id_oficina = ?) as de_origen
            from responsable r
            join oficina o      on o.id_oficina = r.id_oficina and not o.es_custodia and o._estado = 'ACTIVO'
            left join persona pe on pe.id_persona = r.id_persona
            left join cargo c    on c.id_cargo = r.id_cargo
            where o.id_predio = ? and not r.es_custodia and r._estado = 'ACTIVO'
              and (? = '' or lower(concat_ws(' ', r.codigo_funcionario, pe.nombre, pe.paterno, pe.materno, pe.ci, o.nombre,
                                              cast(o.cod_ofi as text))) like ?)
            order by original desc, de_origen desc, nombre, o.cod_ofi
            limit ?
            """;
        return jdbc.query(sql, (rs, n) -> new CustodiaDTOs.Destino(
                rs.getLong("id_responsable"), rs.getString("codigo_funcionario"), rs.getString("nombre"), rs.getString("ci"), rs.getString("cargo"),
                rs.getLong("id_oficina"), rs.getObject("cod_ofi", Short.class), rs.getString("oficina"),
                rs.getBoolean("original")),
                idResponsableOriginal, idOficinaOrigen != null ? idOficinaOrigen : -1L,
                idPredio, q, "%" + q + "%", tope);
    }

    // ── Reporte de custodia por predio ───────────────────────────────────────

    public List<CustodiaDTOs.PredioCustodia> prediosConCustodia() {
        String sql = """
            select p.id_predio, p.unidad, p.descrip as predio, o.id_oficina, o.cod_ofi, o.nombre as oficina,
                   (select count(*) from activo a where a.id_oficina = o.id_oficina and a._estado = 'ACTIVO') as bienes
            from oficina o
            join predio p on p.id_predio = o.id_predio
            where o.es_custodia and o._estado = 'ACTIVO'
            order by p.descrip
            """;
        return jdbc.query(sql, (rs, n) -> new CustodiaDTOs.PredioCustodia(
                rs.getLong("id_predio"), rs.getString("unidad"), rs.getString("predio"),
                rs.getLong("id_oficina"), rs.getObject("cod_ofi", Short.class), rs.getString("oficina"),
                rs.getLong("bienes")));
    }

    /**
     * Lo que hay hoy en la oficina de faltantes del predio, con el faltante de cada bien: el
     * pendiente si lo hay, si no el último resuelto (pendiente de baja). Sin faltante = histórico.
     */
    public List<CustodiaDTOs.BienCustodia> bienesEnCustodia(Long idPredio) {
        String sql = """
            select a.id_activo, a.codigo, a.descripcion, pe.id_persona,
                   trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as persona, pe.ci,
                   -- Un regularizado sin origen conocido apunta a la propia oficina de faltantes: no es un origen.
                   case when oo.es_custodia then null else oo.cod_ofi end as cod_ofi_origen,
                   case when oo.es_custodia then null else oo.nombre end  as oficina_origen,
                   af.numero as numero_acta, h.fecha_envio_custodia, h.estado_hallazgo, h.tipo_resolucion
            from activo a
            join oficina o       on o.id_oficina = a.id_oficina and o.es_custodia
            left join responsable r on r.id_responsable = a.id_responsable
            left join persona pe on pe.id_persona = r.id_persona
            left join lateral (
                 select h.* from hallazgo_inventario h
                 where h.id_activo = a.id_activo and h.tipo_hallazgo = 'FALTANTE'
                   and h.estado_hallazgo in ('ABIERTO', 'EN_CUSTODIA', 'RESUELTO')
                 order by (h.estado_hallazgo <> 'RESUELTO') desc, h.id_hallazgo desc
                 limit 1) h on true
            left join acta_faltante af on af.id_acta = h.id_acta
            left join inventario i     on i.id_inventario = h.id_inventario
            left join oficina oo       on oo.id_oficina = coalesce(h.id_oficina_origen, i.id_oficina)
            where o.id_predio = ? and a._estado = 'ACTIVO'
            order by persona, oo.cod_ofi nulls last, a.codigo
            """;
        return jdbc.query(sql, (rs, n) -> {
            java.sql.Timestamp env = rs.getTimestamp("fecha_envio_custodia");
            return new CustodiaDTOs.BienCustodia(
                    rs.getLong("id_activo"), rs.getString("codigo"), rs.getString("descripcion"),
                    rs.getObject("id_persona", Long.class), rs.getString("persona"), rs.getString("ci"),
                    rs.getObject("cod_ofi_origen", Short.class), rs.getString("oficina_origen"),
                    rs.getString("numero_acta"), env == null ? null : env.toLocalDateTime(),
                    rs.getString("estado_hallazgo"), rs.getString("tipo_resolucion"));
        }, idPredio);
    }

    // ── Consolidado por persona ──────────────────────────────────────────────

    /** Faltantes (sin anulados) por persona y predio de origen. */
    public List<CustodiaDTOs.ConsolidadoFila> consolidado(boolean soloPendientes) {
        String sql = """
            select pe.id_persona, trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as persona, pe.ci,
                   p.unidad, p.descrip as predio,
                   count(*) filter (where h.estado_hallazgo = 'ABIERTO')     as abiertos,
                   count(*) filter (where h.estado_hallazgo = 'EN_CUSTODIA') as en_custodia,
                   count(*) filter (where h.estado_hallazgo = 'RESUELTO')    as resueltos
            from hallazgo_inventario h
            left join inventario i on i.id_inventario = h.id_inventario
            join oficina o         on o.id_oficina = coalesce(h.id_oficina_origen, i.id_oficina)
            join predio p          on p.id_predio = o.id_predio
            join responsable r     on r.id_responsable = h.id_responsable
            join persona pe        on pe.id_persona = r.id_persona
            where h.tipo_hallazgo = 'FALTANTE' and h.estado_hallazgo in ('ABIERTO', 'EN_CUSTODIA', 'RESUELTO')
            group by pe.id_persona, pe.nombre, pe.paterno, pe.materno, pe.ci, p.unidad, p.descrip
            %s
            order by persona, p.unidad
            """.formatted(soloPendientes
                ? "having count(*) filter (where h.estado_hallazgo in ('ABIERTO', 'EN_CUSTODIA')) > 0" : "");
        return jdbc.query(sql, (rs, n) -> new CustodiaDTOs.ConsolidadoFila(
                rs.getLong("id_persona"), rs.getString("persona"), rs.getString("ci"),
                rs.getString("unidad"), rs.getString("predio"),
                rs.getLong("abiertos"), rs.getLong("en_custodia"), rs.getLong("resueltos")));
    }

    // ── Conciliación ─────────────────────────────────────────────────────────

    private static final String SELECT_ALERTA = """
            select h.id_hallazgo, a.id_activo, a.codigo, a.descripcion,
                   trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as persona,
                   p.unidad, (o.cod_ofi || ' — ' || o.nombre) as oficina_actual,
                   af.numero as numero_acta, af.id_acta, %s as desde
            """;

    private RowMapper<CustodiaDTOs.Alerta> mapperAlerta(String tipo, String detalle) {
        return (rs, n) -> {
            java.sql.Timestamp d = rs.getTimestamp("desde");
            String det = detalle != null ? detalle : rs.getString("detalle");
            return new CustodiaDTOs.Alerta(tipo, rs.getObject("id_hallazgo", Long.class), rs.getLong("id_activo"),
                    rs.getString("codigo"), rs.getString("descripcion"), rs.getString("persona"),
                    rs.getString("unidad"), rs.getString("oficina_actual"), rs.getString("numero_acta"),
                    rs.getObject("id_acta", Long.class), d == null ? null : d.toLocalDateTime(), det);
        };
    }

    /**
     * El faltante dice EN_CUSTODIA pero el bien ya no está en la oficina de faltantes: lo
     * movieron en el VSIAF (la sincronización lo trajo) o por un camino anterior al bloqueo.
     */
    public List<CustodiaDTOs.Alerta> enCustodiaFueraDeCustodia() {
        String sql = SELECT_ALERTA.formatted("h.fecha_envio_custodia") + """
            from hallazgo_inventario h
            join activo a              on a.id_activo = h.id_activo
            join oficina o             on o.id_oficina = a.id_oficina
            join predio p              on p.id_predio = o.id_predio
            left join responsable ra   on ra.id_responsable = a.id_responsable
            left join responsable r    on r.id_responsable = h.id_responsable
            left join persona pe       on pe.id_persona = r.id_persona
            left join acta_faltante af on af.id_acta = h.id_acta
            where h.estado_hallazgo = 'EN_CUSTODIA' and not o.es_custodia and not coalesce(ra.es_custodia, false)
            order by p.unidad, a.codigo
            """;
        return jdbc.query(sql, mapperAlerta("FUERA_DE_CUSTODIA",
                "El faltante figura en custodia, pero el bien está en otra oficina: lo movieron fuera del SCIAF."));
    }

    /**
     * Bienes en una oficina de faltantes sin acta: los históricos (antes del SCIAF) o los que
     * alguien pasó a mano en el VSIAF.
     */
    public List<CustodiaDTOs.Alerta> enCustodiaSinRegistro() {
        String sql = SELECT_ALERTA.formatted("a.fecha_ult") + """
            from activo a
            join oficina o           on o.id_oficina = a.id_oficina and o.es_custodia
            join predio p            on p.id_predio = o.id_predio
            left join responsable r  on r.id_responsable = a.id_responsable
            left join persona pe     on pe.id_persona = r.id_persona
            left join hallazgo_inventario h on false
            left join acta_faltante af on false
            where a._estado = 'ACTIVO'
              and not exists (select 1 from hallazgo_inventario x
                               where x.id_activo = a.id_activo and x.tipo_hallazgo = 'FALTANTE'
                                 and x.id_acta is not null and x.estado_hallazgo <> 'ANULADO')
            order by p.unidad, persona, a.codigo
            """;
        return jdbc.query(sql, mapperAlerta("SIN_REGISTRO",
                "Está en la oficina de faltantes sin acta: histórico o cargado a mano en el VSIAF."));
    }

    /**
     * Bienes en una oficina de faltantes sin acta (históricos), con la oficina de la que
     * vinieron según el historial del SCIAF (el último movimiento hacia esa oficina). La
     * persona es la que figura en la oficina de faltantes.
     *
     * @param idResponsableCustodia null = todos
     */
    public List<CustodiaDTOs.HistoricoFila> historicos(Long idResponsableCustodia) {
        String sql = """
            select r.id_responsable, pe.id_persona, trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) as persona,
                   pe.ci, p.unidad, p.descrip as predio, o.cod_ofi, o.nombre as oficina,
                   a.id_activo, a.codigo, a.descripcion,
                   oo.id_oficina as id_oficina_origen, oo.cod_ofi as cod_ofi_origen, oo.nombre as oficina_origen,
                   hi.fecha_evento
            from activo a
            join oficina o      on o.id_oficina = a.id_oficina and o.es_custodia
            join predio p       on p.id_predio = o.id_predio
            join responsable r  on r.id_responsable = a.id_responsable
            left join persona pe on pe.id_persona = r.id_persona
            left join lateral (
                 select h.id_oficina_anterior, h.fecha_evento from historial_activo h
                 where h.id_activo = a.id_activo and h.id_oficina_nueva = a.id_oficina
                 order by h.fecha_evento desc limit 1) hi on true
            left join oficina oo on oo.id_oficina = hi.id_oficina_anterior and not oo.es_custodia
            where a._estado = 'ACTIVO'
              and (cast(? as bigint) is null or r.id_responsable = ?)
              and not exists (select 1 from hallazgo_inventario x
                               where x.id_activo = a.id_activo and x.tipo_hallazgo = 'FALTANTE'
                                 and (x.id_acta is not null or x.estado_hallazgo in ('ABIERTO', 'EN_CUSTODIA'))
                                 and x.estado_hallazgo <> 'ANULADO')
            order by p.unidad, persona, a.codigo
            """;
        return jdbc.query(sql, (rs, n) -> {
            java.sql.Timestamp f = rs.getTimestamp("fecha_evento");
            return new CustodiaDTOs.HistoricoFila(
                    rs.getLong("id_responsable"), rs.getObject("id_persona", Long.class), rs.getString("persona"),
                    rs.getString("ci"), rs.getString("unidad"), rs.getString("predio"),
                    rs.getObject("cod_ofi", Short.class), rs.getString("oficina"),
                    rs.getLong("id_activo"), rs.getString("codigo"), rs.getString("descripcion"),
                    rs.getObject("id_oficina_origen", Long.class), rs.getObject("cod_ofi_origen", Short.class),
                    rs.getString("oficina_origen"), f == null ? null : f.toLocalDateTime());
        }, idResponsableCustodia, idResponsableCustodia);
    }

    /** Traslados con error, o que llevan más de {@code minutos} sin avanzar. */
    public List<CustodiaDTOs.Alerta> trasladosTrabados(int minutos) {
        String sql = SELECT_ALERTA.formatted("coalesce(h.fecha_envio_custodia, af.fecha_emision)")
            + ", h.estado_envio, h.mensaje_envio, "
            + "case h.estado_envio when 'ERROR' then coalesce(h.mensaje_envio, 'El VSIAF rechazó el traslado.') "
            + "     when 'ENVIADO' then 'El VSIAF no confirma el traslado: revise el worker.' "
            + "     else coalesce(h.mensaje_envio, 'La custodia del predio no se confirma en el VSIAF.') end as detalle "
            + """
            from hallazgo_inventario h
            join activo a              on a.id_activo = h.id_activo
            join oficina o             on o.id_oficina = a.id_oficina
            join predio p              on p.id_predio = o.id_predio
            left join responsable r    on r.id_responsable = h.id_responsable
            left join persona pe       on pe.id_persona = r.id_persona
            left join acta_faltante af on af.id_acta = h.id_acta
            where h.estado_hallazgo = 'ABIERTO'
              and (h.estado_envio = 'ERROR'
                   or (h.estado_envio = 'ENVIADO' and h.fecha_envio_custodia < now() - make_interval(mins => ?))
                   or (h.estado_envio = 'ESPERANDO_ALTA' and af.fecha_emision < now() - make_interval(mins => ?)))
            order by desde
            """;
        return jdbc.query(sql, mapperAlerta("TRASLADO_TRABADO", null), minutos, minutos);
    }

    /** Bienes vigentes a cargo de la persona, en todas sus oficinas, fuera de la custodia. */
    public List<BienPersonaDTO> bienesDePersona(Long idPersona) {
        String sql = """
            select a.id_activo, a.codigo, a.descripcion, ea.nombre as estado_activo,
                   r.id_responsable, r.codigo_funcionario, c.nombre as cargo,
                   o.id_oficina, o.cod_ofi, o.nombre as oficina,
                   p.id_predio, p.unidad, p.descrip as predio,
                   coalesce(a.bloqueado, false) as bloqueado,
                   h.id_hallazgo, h.estado_hallazgo, af.numero as numero_acta, i.numero_inventario
            from activo a
            join responsable r         on r.id_responsable   = a.id_responsable and not r.es_custodia
            join oficina o             on o.id_oficina       = a.id_oficina and not o.es_custodia
            join predio p              on p.id_predio        = o.id_predio
            left join cargo c          on c.id_cargo         = r.id_cargo
            left join estado_activo ea on ea.id_estado_activo = a.id_estado_activo
            left join hallazgo_inventario h on h.id_activo   = a.id_activo and %s
            left join acta_faltante af on af.id_acta         = h.id_acta
            left join inventario i     on i.id_inventario    = h.id_inventario
            where r.id_persona = ? and a._estado = 'ACTIVO'
            order by p.descrip, o.cod_ofi, a.codigo
            """.formatted(PENDIENTE);
        return jdbc.query(sql, MAPPER_BIEN, idPersona);
    }
}
