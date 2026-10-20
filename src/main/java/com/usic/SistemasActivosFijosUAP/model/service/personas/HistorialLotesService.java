package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.usic.SistemasActivosFijosUAP.componet.VsiafDisponibilidad;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.ReactivarPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.Registro;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.RestaurarCargos;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.RestaurarDatos;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.RestaurarFila;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanReversion.Resultado;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.EjecutorAutorizacion;

/**
 * Pestaña «Historial» de Personas y cargos (fase 5 de docs/PLAN_PERSONAS_UNIFICADAS.md): los
 * cambios agrupados por lote y la reversión de un lote. Se revierte solo si nada cambió
 * después ({@link PlanReversion}); se restaura con SQL directo (no con entidades que la sesión
 * pueda tener de antes), en una transacción con bloqueo, y cada oficina va al VSIAF por la cola.
 */
@Service
public class HistorialLotesService implements EjecutorAutorizacion {

    private static final Logger log = LoggerFactory.getLogger(HistorialLotesService.class);

    public static final String TIPO_SOLICITUD = "LOTE_REVERTIR";
    public static final String MODULO_SOLICITUD = "HISTORIAL";

    private final NamedParameterJdbcTemplate jdbc;
    private final PersonaHistorialService historial;
    private final VsiafApoyoService vsiafApoyoService;
    private final VsiafDisponibilidad vsiaf;
    private final ActividadService actividadService;
    private final EnvioFilasVsiaf envioFilas;
    private final TransactionTemplate tx;

    public HistorialLotesService(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate, PersonaHistorialService historial,
                                 VsiafApoyoService vsiafApoyoService, VsiafDisponibilidad vsiaf,
                                 ActividadService actividadService, EnvioFilasVsiaf envioFilas,
                                 PlatformTransactionManager txManager) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.historial = historial;
        this.vsiafApoyoService = vsiafApoyoService;
        this.vsiaf = vsiaf;
        this.actividadService = actividadService;
        this.envioFilas = envioFilas;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── Lectura ───────────────────────────────────────────────────────────

    /**
     * Lotes, lo más nuevo primero.
     *
     * @param q       texto en la persona, la oficina o el cargo (antes / después)
     * @param usuario quien lo hizo
     */
    public List<Map<String, Object>> lotes(String q, String usuario, LocalDate desde, LocalDate hasta) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder w = new StringBuilder(" WHERE 1 = 1");
        if (q != null && !q.isBlank()) {
            w.append(" AND c.lote IN (SELECT x.lote FROM persona_cambio x LEFT JOIN persona p2 ON p2.id_persona = x.id_persona"
                    + " WHERE x.referencia ILIKE :q OR x.antes ILIKE :q OR x.despues ILIKE :q"
                    + " OR concat_ws(' ', p2.nombre, p2.paterno, p2.materno, p2.ci) ILIKE :q)");
            p.addValue("q", "%" + q.trim() + "%");
        }
        if (usuario != null && !usuario.isBlank()) {
            w.append(" AND c.usuario ILIKE :u");
            p.addValue("u", "%" + usuario.trim() + "%");
        }
        if (desde != null) { w.append(" AND c.fecha >= :desde"); p.addValue("desde", desde.atStartOfDay()); }
        if (hasta != null) { w.append(" AND c.fecha < :hasta"); p.addValue("hasta", hasta.plusDays(1).atStartOfDay()); }
        String sql = "SELECT c.lote, min(c.fecha) AS fecha, max(c.usuario) AS usuario, max(c.origen) AS origen,"
                + " string_agg(DISTINCT c.tipo, ',') AS tipos,"
                + " count(*) FILTER (WHERE c.id_responsable IS NOT NULL) AS oficinas,"
                + " max(c.motivo) AS motivo, max(c.revertido_por) AS revertido_por, max(c.lote_revertido) AS lote_revertido,"
                + " string_agg(DISTINCT nullif(trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)), ''), ' · ') AS personas,"
                + " max(c.referencia) FILTER (WHERE c.tipo = 'CARGO_FUSION') AS cargo,"
                + " count(*) FILTER (WHERE c.estado_vsiaf = 'FALLO') AS fallos"
                + " FROM persona_cambio c LEFT JOIN persona pe ON pe.id_persona = c.id_persona AND c.id_persona <> 0"
                + w + " GROUP BY c.lote ORDER BY min(c.fecha) DESC LIMIT 150";
        return jdbc.query(sql, p, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lote", rs.getString("lote"));
            m.put("fecha", rs.getTimestamp("fecha") == null ? "" : rs.getTimestamp("fecha").toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
            m.put("usuario", rs.getString("usuario"));
            m.put("origen", rs.getString("origen"));
            m.put("tipos", rs.getString("tipos"));
            m.put("oficinas", rs.getLong("oficinas"));
            m.put("motivo", rs.getString("motivo"));
            m.put("revertidoPor", rs.getString("revertido_por"));
            m.put("loteRevertido", rs.getString("lote_revertido"));
            m.put("personas", rs.getString("personas"));
            m.put("cargo", rs.getString("cargo"));
            m.put("fallos", rs.getLong("fallos"));
            String origen = rs.getString("origen");
            m.put("revertible", rs.getString("revertido_por") == null && rs.getString("lote_revertido") == null
                    && (PersonaHistorialService.ORIGEN_PERSONAS_CARGOS.equals(origen) || CargosService.ORIGEN.equals(origen)));
            return m;
        });
    }

    public List<Map<String, Object>> detalle(String lote) {
        return historial.delLote(lote);
    }

    /** Vista previa de la reversión: no guarda nada. */
    public Resultado plan(String lote) {
        List<Registro> registros = new ArrayList<>();
        for (PersonaCambio c : historial.registrosDelLote(lote)) {
            registros.add(new Registro(c.getTipo(), c.getIdResponsable(), c.getIdPersona(), c.getReferencia(),
                    historial.leerJson(c.getAntes()), historial.leerJson(c.getDespues()), c.getOrigen(),
                    c.getLoteRevertido(), c.getRevertidoPor()));
        }
        return PlanReversion.calcular(lote, registros, estadoDe(registros));
    }

    private PlanReversion.Estado estadoDe(List<Registro> registros) {
        Set<Long> filas = new LinkedHashSet<>(), personas = new LinkedHashSet<>(), cargos = new LinkedHashSet<>(),
                actas = new LinkedHashSet<>(), usuarios = new LinkedHashSet<>();
        Set<String> nombres = new LinkedHashSet<>();
        Set<Long> personasConDatos = new LinkedHashSet<>(), elegidos = new LinkedHashSet<>();
        Set<String> cisAntes = new LinkedHashSet<>();
        for (Registro r : registros) {
            if (r.idResponsable() != null) {
                filas.add(r.idResponsable());
                Long ca = PlanReversion.num(r.antes().get("idCargo")), cd = PlanReversion.num(r.despues().get("idCargo"));
                if (ca != null) cargos.add(ca);
                if (cd != null) cargos.add(cd);
            }
            if (r.idResponsable() == null && PersonaCambio.TIPO_PERSONA_NUEVA.equals(r.tipo())) {
                Long id = PlanReversion.num(r.despues().get("idPersona"));
                if (id != null) { personas.add(id); personasConDatos.add(id); }
            }
            if (r.idResponsable() == null && PersonaCambio.TIPO_DATOS.equals(r.tipo())) {
                Long id = PlanReversion.num(r.antes().get("idPersona"));
                if (id != null) personasConDatos.add(id);
                String n = IdentidadPersona.numeroCi(r.antes().get("ci") == null ? null : r.antes().get("ci").toString(), Set.of());
                if (n != null) cisAntes.add(n);
            }
            Long ip = PlanReversion.num(r.antes().get("idPersona"));
            if (ip != null) personas.add(ip);
            Long ip2 = PlanReversion.num(r.despues().get("idPersona"));
            if (ip2 != null) personas.add(ip2);
            Long fe = PlanReversion.num(r.despues().get("fusionadaEn"));
            if (fe != null) personas.add(fe);
            actas.addAll(PlanReversion.nums(r.despues().get("actas")));
            usuarios.addAll(PlanReversion.nums(r.despues().get("usuarios")));
            Long el = PlanReversion.num(r.antes().get("idElegido"));
            if (el != null) { cargos.add(el); elegidos.add(el); }
            Long al = PlanReversion.num(r.despues().get("idAlias"));
            if (al != null) cargos.add(al);
            if (r.antes().get("nombreElegido") != null) nombres.add(PlanCargos.limpio(r.antes().get("nombreElegido").toString()));
            if (r.antes().get("juntados") instanceof java.util.Collection<?> js) {
                for (Object o : js) if (o instanceof Map<?, ?> m) {
                    Long id = PlanReversion.num(m.get("id"));
                    if (id != null) cargos.add(id);
                    if (m.get("nombre") != null) nombres.add(PlanCargos.limpio(m.get("nombre").toString()));
                }
            }
        }

        Map<Long, PlanReversion.FilaHoy> f = new HashMap<>();
        if (!filas.isEmpty()) {
            List<Object[]> crudas = jdbc.query("""
                    SELECT r.id_responsable, r.id_persona, r.id_cargo, r._estado, r.pendiente_dbf,
                           pr.unidad, o.cod_ofi, r.codigo_funcionario
                      FROM responsable r
                      LEFT JOIN oficina o ON o.id_oficina = r.id_oficina
                      LEFT JOIN predio pr ON pr.id_predio = o.id_predio
                     WHERE r.id_responsable IN (:ids)""", Map.of("ids", filas), (rs, i) -> new Object[] {
                    rs.getLong("id_responsable"), (Long) rs.getObject("id_persona", Long.class),
                    (Long) rs.getObject("id_cargo", Long.class), rs.getString("_estado"), rs.getBoolean("pendiente_dbf"),
                    rs.getString("unidad") + " " + rs.getObject("cod_ofi") + " · cód. " + rs.getString("codigo_funcionario") });
            Map<Long, Boolean> pend = new HashMap<>();
            for (Object[] o : crudas) pend.put((Long) o[0], (Boolean) o[4]);
            Map<Long, VsiafApoyoService.EstadoVsiaf> est = vsiafApoyoService.estados(VsiafApoyoService.TABLA_RESP, pend);
            for (Object[] o : crudas) {
                VsiafApoyoService.EstadoVsiaf e = est.get((Long) o[0]);
                f.put((Long) o[0], new PlanReversion.FilaHoy((Long) o[1], (Long) o[2], (String) o[3], (String) o[5],
                        (Boolean) o[4], e == null ? VsiafApoyoService.EST_VSIAF : e.codigo()));
            }
        }
        Map<Long, PlanReversion.PersonaHoy> p = new HashMap<>();
        if (!personas.isEmpty()) {
            jdbc.query("SELECT id_persona, nombre, paterno, materno, ci, _estado, fusionada_en FROM persona WHERE id_persona IN (:ids)",
                    Map.of("ids", personas), rs -> {
                        p.put(rs.getLong("id_persona"), new PlanReversion.PersonaHoy(rs.getString("nombre"), rs.getString("paterno"),
                                rs.getString("materno"), rs.getString("ci"), rs.getString("_estado"),
                                rs.getObject("fusionada_en", Long.class)));
                    });
        }
        Map<Long, PlanReversion.CargoHoy> c = new HashMap<>();
        if (!cargos.isEmpty()) {
            jdbc.query("SELECT id_cargo, nombre, _estado, fusionado_en FROM cargo WHERE id_cargo IN (:ids)",
                    Map.of("ids", cargos), rs -> {
                        c.put(rs.getLong("id_cargo"), new PlanReversion.CargoHoy(rs.getString("nombre"), rs.getString("_estado"),
                                rs.getObject("fusionado_en", Long.class)));
                    });
        }
        Map<Long, Long> a = new HashMap<>();
        if (!actas.isEmpty()) {
            jdbc.query("SELECT id_acta, id_persona FROM acta_faltante WHERE id_acta IN (:ids)", Map.of("ids", actas),
                    rs -> { a.put(rs.getLong("id_acta"), rs.getObject("id_persona", Long.class)); });
        }
        Map<Long, Long> u = new HashMap<>();
        if (!usuarios.isEmpty()) {
            jdbc.query("SELECT id_usuario, id_persona FROM usuario WHERE id_usuario IN (:ids)", Map.of("ids", usuarios),
                    rs -> { u.put(rs.getLong("id_usuario"), rs.getObject("id_persona", Long.class)); });
        }
        Map<String, List<Long>> vigentes = new HashMap<>();
        for (String n : nombres) {
            if (n == null || n.isEmpty()) continue;
            vigentes.put(n, jdbc.queryForList("SELECT id_cargo FROM cargo WHERE upper(trim(nombre)) = :n"
                    + " AND coalesce(_estado, 'ACTIVO') = 'ACTIVO' ORDER BY id_cargo", Map.of("n", n), Long.class));
        }
        // Oficinas de la persona / del cargo que NO están en el lote (llegaron después).
        Map<Long, Long> fueraPersona = new HashMap<>(), fueraCargo = new HashMap<>();
        List<Long> delLote = filas.isEmpty() ? List.of(-1L) : new ArrayList<>(filas);
        for (Long id : personasConDatos) {
            fueraPersona.put(id, jdbc.queryForObject("SELECT count(*) FROM responsable WHERE _estado = 'ACTIVO' AND id_persona = :p"
                    + " AND id_responsable NOT IN (:l)", new MapSqlParameterSource().addValue("p", id).addValue("l", delLote), Long.class));
        }
        for (Long id : elegidos) {
            fueraCargo.put(id, jdbc.queryForObject("SELECT count(*) FROM responsable WHERE _estado = 'ACTIVO' AND id_cargo = :c"
                    + " AND id_responsable NOT IN (:l)", new MapSqlParameterSource().addValue("c", id).addValue("l", delLote), Long.class));
        }
        // Quién tiene hoy el C.I. de antes (mismo criterio que PersonasCargosService: número, sin fusionadas).
        Map<String, List<Long>> conCi = new HashMap<>();
        for (String n : cisAntes) {
            conCi.put(n, jdbc.queryForList("SELECT id_persona FROM persona WHERE _estado NOT IN ('FUSIONADA', 'ELIMINADO')"
                    + " AND substring(regexp_replace(split_part(coalesce(ci, ''), '-', 1), '[.\\s]', '', 'g') from '^[0-9]+') = :n",
                    Map.of("n", n), Long.class));
        }
        Set<Long> conUsuario = new java.util.HashSet<>();
        if (!personas.isEmpty()) conUsuario.addAll(jdbc.queryForList(
                "SELECT id_persona FROM usuario WHERE id_persona IN (:ids)", Map.of("ids", personas), Long.class));
        // Lo que cuelga de una persona creada por separación (no se da de baja si tiene algo).
        if (!personasConDatos.isEmpty()) {
            conUsuario.addAll(jdbc.queryForList("SELECT DISTINCT fusionada_en FROM persona WHERE fusionada_en IN (:ids)",
                    Map.of("ids", personasConDatos), Long.class));
            conUsuario.addAll(jdbc.queryForList("SELECT DISTINCT id_persona FROM acta_faltante WHERE id_persona IN (:ids)",
                    Map.of("ids", personasConDatos), Long.class));
        }
        return new PlanReversion.Estado(f, p, c, a, u, vigentes, fueraPersona, fueraCargo, conCi, conUsuario);
    }

    // ── Escritura ─────────────────────────────────────────────────────────

    public record Aplicado(boolean ok, String msg, String lote) {
    }

    public Aplicado revertirDirecto(String lote, String firma, String motivo, Usuario autor) {
        if (!RolesSciaf.esAdministrativo(autor)) {
            throw new IllegalStateException("Solo ADMINISTRADOR o SUPER USUARIO revierten directo; el resto envía una solicitud.");
        }
        return revertir(lote, firma, motivo, autor);
    }

    private Aplicado revertir(String lote, String firmaVista, String motivo, Usuario autor) {
        if (!vsiaf.dbf("Personas y cargos")) {
            throw new IllegalStateException("No se aplica desde aquí: " + vsiaf.motivoDbf() + " Hágalo desde el servidor del SCIAF.");
        }
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Indique el motivo de la reversión.");
        String nuevo = PersonaHistorialService.nuevoLote();
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        Resultado[] hecho = new Resultado[1];
        List<PersonaCambio> porFila = tx.execute(st -> {
            // Bloqueos: el lote (dos reversiones a la vez) y todo lo que toca, siempre en el mismo orden.
            jdbc.queryForList("SELECT id_cambio FROM persona_cambio WHERE lote = :l ORDER BY id_cambio FOR UPDATE",
                    Map.of("l", lote), Long.class);
            Resultado p = plan(lote);
            bloquear(p);
            p = plan(lote);
            if (firmaVista == null || !firmaVista.equals(p.firma())) {
                throw new IllegalStateException("Los datos cambiaron desde la vista previa. Vuelva a revisarla.");
            }
            if (!p.errores().isEmpty()) throw new IllegalStateException(String.join(" ", p.errores()));
            hecho[0] = p;
            return aplicarEnBase(p, lote, nuevo, motivo, autor);
        });
        Resultado plan = hecho[0];
        Map<Long, String> estadoAntes = new HashMap<>();
        for (RestaurarFila f : plan.filas()) estadoAntes.put(f.idResponsable(), f.estadoVsiaf());

        EnvioFilasVsiaf.Resumen envio = envioFilas.enviar(porFila, estadoAntes, usuarioNombre);
        try {
            historial.guardarTodos(porFila);
            actividadService.registrar(autor, ActividadService.MOD_PERSONA, ActividadService.ACC_MODIFICACION,
                    "Lote " + lote, "Revirtió el lote " + lote + " (" + resumen(plan) + "). Motivo: " + motivo.trim(),
                    Math.max(plan.filas().size(), 1), null);
        } catch (Exception ex) {
            log.error("[HISTORIAL] Reversión {} aplicada, pero no se pudo anotar el estado del envío: {}", nuevo, ex.getMessage(), ex);
        }
        String msg = "Lote revertido en el SCIAF (" + plan.filas().size() + " oficina(s))." + envio.texto();
        return new Aplicado(envio.fallidos() == 0, msg, nuevo);
    }

    /** FOR UPDATE de oficinas, personas y cargos que toca la reversión (en orden de id). */
    private void bloquear(Resultado p) {
        Set<Long> filas = new java.util.TreeSet<>(), personas = new java.util.TreeSet<>(), cargos = new java.util.TreeSet<>();
        for (RestaurarFila f : p.filas()) {
            filas.add(f.idResponsable());
            // También a quién y a qué cargo vuelve (y de dónde sale): otro cambio que los toque espera.
            if (f.idPersona() != null) personas.add(f.idPersona());
            if (f.idPersonaHoy() != null) personas.add(f.idPersonaHoy());
            if (f.idCargo() != null) cargos.add(f.idCargo());
            if (f.idCargoHoy() != null) cargos.add(f.idCargoHoy());
        }
        for (RestaurarDatos d : p.datos()) personas.add(d.idPersona());
        for (PlanReversion.BajaPersona bp : p.bajas()) personas.add(bp.idPersona());
        for (ReactivarPersona r : p.personas()) { personas.add(r.idPersona()); personas.add(r.desde()); }
        for (RestaurarCargos c : p.cargos()) { cargos.add(c.idElegido()); cargos.addAll(c.reactivar()); }
        if (!filas.isEmpty()) jdbc.queryForList("SELECT id_responsable FROM responsable WHERE id_responsable IN (:i) ORDER BY 1 FOR UPDATE", Map.of("i", filas), Long.class);
        if (!personas.isEmpty()) jdbc.queryForList("SELECT id_persona FROM persona WHERE id_persona IN (:i) ORDER BY 1 FOR UPDATE", Map.of("i", personas), Long.class);
        if (!cargos.isEmpty()) jdbc.queryForList("SELECT id_cargo FROM cargo WHERE id_cargo IN (:i) ORDER BY 1 FOR UPDATE", Map.of("i", cargos), Long.class);
    }

    private List<PersonaCambio> aplicarEnBase(Resultado p, String lote, String nuevo, String motivo, Usuario autor) {
        Long idUsr = autor != null ? autor.getIdUsuario() : null;
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        String origen = PersonaHistorialService.ORIGEN_HISTORIAL;
        String mot = motivo.length() > 500 ? motivo.substring(0, 500) : motivo;
        List<PersonaCambio> otros = new ArrayList<>();

        // 1. Cargos: el elegido recupera su nombre, los juntados vuelven, el alias se da de baja.
        for (RestaurarCargos c : p.cargos()) {
            if (c.nombreAntes() != null) {
                jdbc.update("UPDATE cargo SET nombre = :n, _estado = 'ACTIVO', _modificacion_id_usuario = :u, _fecha_modificacion = now() WHERE id_cargo = :id",
                        new MapSqlParameterSource().addValue("n", c.nombreAntes()).addValue("u", idUsr).addValue("id", c.idElegido()));
            }
            if (!c.reactivar().isEmpty()) {
                jdbc.update("UPDATE cargo SET _estado = 'ACTIVO', fusionado_en = NULL, _modificacion_id_usuario = :u,"
                        + " _fecha_modificacion = now() WHERE id_cargo IN (:ids)",
                        new MapSqlParameterSource().addValue("u", idUsr).addValue("ids", c.reactivar()));
            }
            if (c.idAlias() != null && c.nombreAntes() != null) {
                jdbc.update("UPDATE cargo SET _estado = 'ELIMINADO', _modificacion_id_usuario = :u, _fecha_modificacion = now()"
                        + " WHERE id_cargo = :id AND _estado = 'FUSIONADO'",
                        new MapSqlParameterSource().addValue("u", idUsr).addValue("id", c.idAlias()));
            }
            PersonaCambio h = historial.nuevo(nuevo, 0L, PersonaCambio.TIPO_REVERSION + "+" + PersonaCambio.TIPO_CARGO_FUSION, autor, origen);
            h.setReferencia("Cargo «" + c.nombreHoy() + "» (id " + c.idElegido() + ")");
            Map<String, Object> an = new LinkedHashMap<>();   // Map.of no acepta null
            an.put("nombreElegido", c.nombreHoy());
            h.setAntes(historial.json(an));
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("nombreElegido", c.nombreAntes() != null ? c.nombreAntes() : c.nombreHoy());
            d.put("reactivados", c.reactivar());
            h.setDespues(historial.json(d));
            h.setEstadoVsiaf(PersonaCambio.VSIAF_NO_APLICA);
            otros.add(h);
        }

        // 2. Personas sumadas: vuelven a estar vigentes, con sus actas y su usuario.
        for (ReactivarPersona r : p.personas()) {
            jdbc.update("UPDATE persona SET _estado = 'ACTIVO', fusionada_en = NULL, _modificacion_id_usuario = :u,"
                    + " _fecha_modificacion = now() WHERE id_persona = :id",
                    new MapSqlParameterSource().addValue("u", idUsr).addValue("id", r.idPersona()));
            if (!r.actas().isEmpty()) {
                jdbc.update("UPDATE acta_faltante SET id_persona = :p WHERE id_acta IN (:ids)",
                        new MapSqlParameterSource().addValue("p", r.idPersona()).addValue("ids", r.actas()));
            }
            if (!r.usuarios().isEmpty()) {
                jdbc.update("UPDATE usuario SET id_persona = :p WHERE id_usuario IN (:ids)",
                        new MapSqlParameterSource().addValue("p", r.idPersona()).addValue("ids", r.usuarios()));
            }
            PersonaCambio h = historial.nuevo(nuevo, r.idPersona(), PersonaCambio.TIPO_REVERSION + "+" + PersonaCambio.TIPO_FUSION_PERSONA, autor, origen);
            h.setReferencia(r.nombre() + " (id " + r.idPersona() + ") vuelve a estar vigente");
            h.setAntes(historial.json(Map.of("estado", PersonasCargosService.ESTADO_FUSIONADA, "fusionadaEn", r.desde())));
            h.setDespues(historial.json(Map.of("estado", "ACTIVO", "actas", r.actas(), "usuarios", r.usuarios())));
            h.setEstadoVsiaf(PersonaCambio.VSIAF_NO_APLICA);
            otros.add(h);
        }

        // 3. Nombre / C.I. de la principal.
        for (RestaurarDatos d : p.datos()) {
            jdbc.update("UPDATE persona SET nombre = :n, paterno = :pa, materno = :ma, ci = :ci, _modificacion_id_usuario = :u,"
                    + " _fecha_modificacion = now() WHERE id_persona = :id",
                    new MapSqlParameterSource().addValue("n", vacio(d.nombre())).addValue("pa", vacio(d.paterno()))
                            .addValue("ma", vacio(d.materno())).addValue("ci", vacio(d.ci())).addValue("u", idUsr)
                            .addValue("id", d.idPersona()));
            PersonaCambio h = historial.nuevo(nuevo, d.idPersona(), PersonaCambio.TIPO_REVERSION + "+" + PersonaCambio.TIPO_DATOS, autor, origen);
            h.setReferencia("Nombre / C.I. de la persona " + d.idPersona());
            h.setAntes(historial.json(d.hoy()));
            Map<String, Object> dd = new LinkedHashMap<>();
            dd.put("nombre", d.nombre());
            dd.put("paterno", d.paterno());
            dd.put("materno", d.materno());
            dd.put("ci", d.ci());
            h.setDespues(historial.json(dd));
            h.setEstadoVsiaf(PersonaCambio.VSIAF_NO_APLICA);
            otros.add(h);
        }

        // 4. Oficinas: vuelven a la persona y al cargo de antes.
        List<PersonaCambio> porFila = new ArrayList<>();
        for (RestaurarFila f : p.filas()) {
            // pendiente_dbf mientras su UPDATE viaja: la sync no la pisa con el dato viejo del DBF
            // (EnvioFilasVsiaf la desmarca al encolarla bien).
            jdbc.update("UPDATE responsable SET id_persona = :p, id_cargo = :c, fecha_ult = current_date, usuario = :un,"
                    + " pendiente_dbf = (pendiente_dbf OR :marca), _modificacion_id_usuario = :u, _fecha_modificacion = now()"
                    + " WHERE id_responsable = :id",
                    new MapSqlParameterSource().addValue("p", f.idPersona()).addValue("c", f.idCargo())
                            .addValue("un", usuarioNombre).addValue("u", idUsr).addValue("id", f.idResponsable())
                            .addValue("marca", f.enviaVsiaf()));
            PersonaCambio h = historial.nuevo(nuevo, f.idPersona() == null ? 0L : f.idPersona(), PersonaCambio.TIPO_REVERSION, autor, origen);
            h.setIdResponsable(f.idResponsable());
            h.setReferencia(f.referencia());
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("idPersona", f.idPersonaHoy());
            a.put("idCargo", f.idCargoHoy());
            a.put("cargo", f.cargoHoy());
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("idPersona", f.idPersona());
            d.put("idCargo", f.idCargo());
            d.put("cargo", f.cargo());
            h.setAntes(historial.json(a));
            h.setDespues(historial.json(d));
            porFila.add(h);
        }

        // 5. Personas creadas por una separación: ya sin oficinas, se dan de baja (no se borran).
        for (PlanReversion.BajaPersona bp : p.bajas()) {
            jdbc.update("UPDATE persona SET _estado = 'ELIMINADO', _modificacion_id_usuario = :u, _fecha_modificacion = now()"
                    + " WHERE id_persona = :id AND _estado = 'ACTIVO'",
                    new MapSqlParameterSource().addValue("u", idUsr).addValue("id", bp.idPersona()));
            PersonaCambio h = historial.nuevo(nuevo, bp.idPersona(), PersonaCambio.TIPO_REVERSION + "+" + PersonaCambio.TIPO_PERSONA_NUEVA, autor, origen);
            h.setReferencia(bp.nombre() + " (id " + bp.idPersona() + ") se da de baja");
            h.setAntes(historial.json(Map.of("estado", "ACTIVO")));
            h.setDespues(historial.json(Map.of("estado", "ELIMINADO")));
            h.setEstadoVsiaf(PersonaCambio.VSIAF_NO_APLICA);
            otros.add(h);
        }

        for (PersonaCambio h : otros) { h.setMotivo(mot); h.setLoteRevertido(lote); }
        for (PersonaCambio h : porFila) { h.setMotivo(mot); h.setLoteRevertido(lote); }
        historial.guardarTodos(otros);
        List<PersonaCambio> guardadas = historial.guardarTodos(porFila);
        jdbc.update("UPDATE persona_cambio SET revertido_por = :n WHERE lote = :l",
                new MapSqlParameterSource().addValue("n", nuevo).addValue("l", lote));
        return guardadas;
    }

    private static String vacio(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    public String resumen(Resultado p) {
        List<String> partes = new ArrayList<>();
        if (!p.filas().isEmpty()) partes.add(p.filas().size() + " oficina(s) vuelven a como estaban");
        if (!p.personas().isEmpty()) partes.add(p.personas().size() + " persona(s) sumada(s) vuelven a estar vigentes");
        if (!p.datos().isEmpty()) partes.add("el nombre / C.I. vuelve al de antes");
        long cargos = p.cargos().stream().mapToLong(c -> c.reactivar().size()).sum();
        if (cargos > 0) partes.add(cargos + " cargo(s) juntado(s) vuelven a estar vigentes");
        if (p.cargos().stream().anyMatch(c -> c.nombreAntes() != null)) partes.add("el cargo recupera su nombre");
        if (!p.bajas().isEmpty()) partes.add(p.bajas().size() + " persona(s) creada(s) por la separación se dan de baja");
        return partes.isEmpty() ? "nada que revertir" : String.join("; ", partes);
    }

    // ── Solicitud de autorización (APOYO) ─────────────────────────────────

    /** Id para la solicitud: la primera entrada del lote (única por lote). */
    public Long idDeLote(String lote) {
        List<Long> l = jdbc.queryForList("SELECT min(id_cambio) FROM persona_cambio WHERE lote = :l", Map.of("l", lote), Long.class);
        return l.isEmpty() ? null : l.get(0);
    }

    @Override
    public Set<String> tipos() {
        return Set.of(TIPO_SOLICITUD);
    }

    @Override
    public String ejecutar(String tipo, Map<String, Object> datos, Usuario autor) {
        Object lote = datos.get("lote"), firma = datos.get("firma"), motivo = datos.get("motivo");
        return revertir(lote == null ? null : lote.toString(), firma == null ? null : firma.toString(),
                motivo == null ? null : motivo.toString(), autor).msg();
    }

    // ── Conversión ────────────────────────────────────────────────────────

    public static Map<String, Object> aMapa(Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("errores", r.errores());
        m.put("firma", r.firma());
        m.put("filasAlVsiaf", r.filasAlVsiaf());
        List<Map<String, Object>> filas = new ArrayList<>();
        for (RestaurarFila f : r.filas()) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("referencia", f.referencia());
            x.put("cambiaPersona", !java.util.Objects.equals(f.idPersonaHoy(), f.idPersona()));
            x.put("idPersonaHoy", f.idPersonaHoy());
            x.put("idPersona", f.idPersona());
            x.put("cargoHoy", f.cargoHoy());
            x.put("cargo", f.cargo());
            x.put("enviaVsiaf", f.enviaVsiaf());
            filas.add(x);
        }
        m.put("filas", filas);
        m.put("personas", r.personas().stream().map(p -> Map.of("nombre", p.nombre(), "actas", p.actas().size(),
                "usuario", !p.usuarios().isEmpty())).toList());
        m.put("datos", r.datos().size());
        m.put("bajas", r.bajas().stream().map(PlanReversion.BajaPersona::nombre).toList());
        m.put("cargos", r.cargos().stream().map(c -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("nombreHoy", c.nombreHoy());
            x.put("nombreAntes", c.nombreAntes());
            x.put("reactivar", c.reactivar().size());
            return x;
        }).toList());
        return m;
    }
}
