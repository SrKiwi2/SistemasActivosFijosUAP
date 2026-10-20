package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import com.usic.SistemasActivosFijosUAP.model.dao.ICargoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ICargoNoDuplicadoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.CargoNoDuplicado;
import com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.personas.AgrupadorCargos.CargoUso;
import com.usic.SistemasActivosFijosUAP.model.service.personas.AgrupadorCargos.Grupo;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanCargos.CambioFila;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanCargos.Decision;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanCargos.Fila;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanCargos.Resultado;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.EjecutorAutorizacion;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Pestaña «Cargos» de Personas y cargos (fase 3 de docs/PLAN_PERSONAS_UNIFICADAS.md): juntar
 * los cargos escritos de distintas formas. El elegido queda (con su texto corregido, si se
 * pidió); los demás quedan FUSIONADOS y sus filas pasan al elegido. Cada fila tocada va al
 * VSIAF por la cola y queda en el historial de su persona.
 * <p>
 * Igual que la ficha: ADMINISTRADOR / SUPER USUARIO aplican directo, el resto deja una
 * solicitud ({@link #ejecutar}); sin VSIAF a la vista no se aplica.
 */
@Service
public class CargosService implements EjecutorAutorizacion {

    private static final Logger log = LoggerFactory.getLogger(CargosService.class);

    public static final String TIPO_SOLICITUD = "CARGO_NORMALIZAR";
    public static final String MODULO_SOLICITUD = "CARGO";
    public static final String ESTADO_FUSIONADO = "FUSIONADO";
    public static final String ORIGEN = "CARGOS";

    private final NamedParameterJdbcTemplate jdbc;
    private final ICargoDao cargoDao;
    private final ICargoNoDuplicadoDao noDuplicadoDao;
    private final IResposableDao responsableDao;
    private final VsiafApoyoService vsiafApoyoService;
    private final VsiafDisponibilidad vsiaf;
    private final ActividadService actividadService;
    private final PersonaHistorialService historial;
    private final EnvioFilasVsiaf envioFilas;
    private final TransactionTemplate tx;
    @PersistenceContext
    private EntityManager em;

    public CargosService(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate, ICargoDao cargoDao,
                         ICargoNoDuplicadoDao noDuplicadoDao, IResposableDao responsableDao,
                         VsiafApoyoService vsiafApoyoService, VsiafDisponibilidad vsiaf,
                         ActividadService actividadService, PersonaHistorialService historial,
                         EnvioFilasVsiaf envioFilas, PlatformTransactionManager txManager) {
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.cargoDao = cargoDao;
        this.noDuplicadoDao = noDuplicadoDao;
        this.responsableDao = responsableDao;
        this.vsiafApoyoService = vsiafApoyoService;
        this.vsiaf = vsiaf;
        this.actividadService = actividadService;
        this.historial = historial;
        this.envioFilas = envioFilas;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── Lectura ───────────────────────────────────────────────────────────

    /** Grupos sugeridos: SEGURO y PROBABLE primero, después REVISAR. */
    public List<Map<String, Object>> grupos() {
        List<CargoUso> uso = usoDeCargos(null);
        Set<String> descartados = new HashSet<>();
        for (CargoNoDuplicado d : noDuplicadoDao.findAll()) descartados.add(AgrupadorCargos.par(d.getIdCargoA(), d.getIdCargoB()));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Grupo g : AgrupadorCargos.agrupar(uso, descartados)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("nivel", g.nivel());
            m.put("filas", g.filas());
            m.put("cargos", g.cargos().stream().map(CargosService::aMapa).toList());
            m.put("sinMarcar", g.sinMarcar());
            Map<String, Object> conf = new LinkedHashMap<>();
            g.conflictos().forEach((k, v) -> conf.put(String.valueOf(k), v));
            m.put("conflictos", conf);
            out.add(m);
        }
        return out;
    }

    /** Buscador para juntar a mano. */
    public List<Map<String, Object>> buscar(String q) {
        String t = q == null ? "" : q.trim().toUpperCase();
        if (t.length() < 2) return List.of();
        return usoDeCargos("%" + t + "%").stream().limit(30).map(CargosService::aMapa).toList();
    }

    private List<CargoUso> usoDeCargos(String filtro) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String where = "";
        if (filtro != null) {
            where = " AND upper(c.nombre) LIKE :q";
            p.addValue("q", filtro);
        }
        String sql = "SELECT c.id_cargo, c.nombre,"
                + " count(r.id_responsable) FILTER (WHERE r._estado = 'ACTIVO') AS filas,"
                + " count(DISTINCT r.id_persona) FILTER (WHERE r._estado = 'ACTIVO') AS personas"
                + " FROM cargo c LEFT JOIN responsable r ON r.id_cargo = c.id_cargo"
                + " WHERE coalesce(c._estado, 'ACTIVO') = 'ACTIVO' AND c.nombre IS NOT NULL" + where
                + " GROUP BY c.id_cargo, c.nombre"
                + (filtro == null ? " HAVING count(r.id_responsable) FILTER (WHERE r._estado = 'ACTIVO') > 0" : "")
                + " ORDER BY filas DESC, c.nombre";
        return jdbc.query(sql, p, (rs, i) -> new CargoUso(rs.getLong("id_cargo"), rs.getString("nombre"),
                rs.getLong("filas"), rs.getLong("personas")));
    }

    /** Plan (vista previa): no guarda nada. */
    public Resultado plan(Decision d) {
        if (d.idElegido() == null) throw new IllegalArgumentException("Elija el cargo que queda.");
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(d.idElegido());
        if (d.idsJuntar() != null) ids.addAll(d.idsJuntar());

        Map<Long, PlanCargos.Cargo> cargos = new HashMap<>();
        jdbc.query("SELECT id_cargo, nombre, _estado FROM cargo WHERE id_cargo IN (:ids)", Map.of("ids", ids), rs -> {
            cargos.put(rs.getLong("id_cargo"), new PlanCargos.Cargo(rs.getLong("id_cargo"), rs.getString("nombre"),
                    rs.getString("_estado")));
        });

        List<Object[]> crudas = jdbc.query("""
                SELECT r.id_responsable, coalesce(r.id_persona, 0) AS id_persona, r.id_cargo, r.pendiente_dbf,
                       trim(concat_ws(' ', pe.nombre, pe.paterno, pe.materno)) AS persona,
                       pr.unidad, o.cod_ofi, r.codigo_funcionario
                  FROM responsable r
                  LEFT JOIN oficina o ON o.id_oficina = r.id_oficina
                  LEFT JOIN predio pr ON pr.id_predio = o.id_predio
                  LEFT JOIN persona pe ON pe.id_persona = r.id_persona
                 WHERE r._estado = 'ACTIVO' AND r.id_cargo IN (:ids)""", Map.of("ids", ids), (rs, i) -> new Object[] {
                rs.getLong("id_responsable"), rs.getLong("id_persona"), rs.getLong("id_cargo"), rs.getBoolean("pendiente_dbf"),
                rs.getString("persona"),
                (rs.getString("unidad") == null ? "?" : rs.getString("unidad")) + " "
                        + (rs.getObject("cod_ofi") == null ? "?" : rs.getObject("cod_ofi")) + " · cód. " + rs.getString("codigo_funcionario") });
        Map<Long, Boolean> pendiente = new HashMap<>();
        for (Object[] o : crudas) pendiente.put((Long) o[0], (Boolean) o[3]);
        Map<Long, VsiafApoyoService.EstadoVsiaf> estados = vsiafApoyoService.estados(VsiafApoyoService.TABLA_RESP, pendiente);
        List<Fila> filas = new ArrayList<>();
        for (Object[] o : crudas) {
            VsiafApoyoService.EstadoVsiaf e = estados.get((Long) o[0]);
            filas.add(new Fila((Long) o[0], (Long) o[1], (String) o[4], (Long) o[2], (String) o[5], (Boolean) o[3],
                    e == null ? VsiafApoyoService.EST_VSIAF : e.codigo()));
        }

        PlanCargos.Cargo elegido = cargos.get(d.idElegido());
        String texto = d.texto() != null ? PlanCargos.limpio(d.texto()) : elegido == null ? null : PlanCargos.limpio(elegido.nombre());
        List<Long> otros = texto == null || texto.isEmpty() ? List.of()
                // Otro cargo con ese texto fuera del grupo: vigente, o juntado en OTRO cargo (si se
                // permitiera, la sync podría mandar estas filas a ese otro). Los alias que ya
                // apuntan al elegido (texto viejo de un renombre anterior) no cuentan.
                : jdbc.queryForList("SELECT id_cargo FROM cargo WHERE upper(trim(nombre)) = :t"
                        + " AND id_cargo NOT IN (:ids)"
                        + " AND (coalesce(_estado, 'ACTIVO') = 'ACTIVO'"
                        + "      OR (_estado = 'FUSIONADO' AND coalesce(fusionado_en, -1) <> :el))",
                        new MapSqlParameterSource().addValue("t", texto).addValue("ids", ids).addValue("el", d.idElegido()), Long.class);
        return PlanCargos.calcular(d, cargos, filas, otros);
    }

    // ── Escritura ─────────────────────────────────────────────────────────

    public record Aplicado(boolean ok, String msg, String lote) {
    }

    /** Aplicación directa: solo ADMINISTRADOR / SUPER USUARIO (se revisa aquí además del controlador). */
    public Aplicado aplicarDirecto(Decision d, String firma, String motivo, Usuario autor) {
        if (!RolesSciaf.esAdministrativo(autor)) {
            throw new IllegalStateException("Solo ADMINISTRADOR o SUPER USUARIO aplican directo; el resto envía una solicitud.");
        }
        return aplicar(d, firma, motivo, autor);
    }

    private Aplicado aplicar(Decision d, String firmaVista, String motivo, Usuario autor) {
        if (!vsiaf.dbf("Personas y cargos")) {
            throw new IllegalStateException("No se aplica desde aquí: " + vsiaf.motivoDbf() + " Hágalo desde el servidor del SCIAF.");
        }
        String lote = PersonaHistorialService.nuevoLote();
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        Resultado[] hecho = new Resultado[1];
        List<PersonaCambio> porFila = tx.execute(st -> {
            // Bloqueo de los cargos (en orden de id) y plan rehecho con lo que hay ahora.
            Set<Long> ids = new LinkedHashSet<>();
            ids.add(d.idElegido());
            if (d.idsJuntar() != null) ids.addAll(d.idsJuntar());
            jdbc.queryForList("SELECT id_cargo FROM cargo WHERE id_cargo IN (:ids) ORDER BY id_cargo FOR UPDATE",
                    Map.of("ids", ids), Long.class);
            jdbc.queryForList("SELECT id_responsable FROM responsable WHERE id_cargo IN (:ids) AND _estado = 'ACTIVO'"
                    + " ORDER BY id_responsable FOR UPDATE", Map.of("ids", ids), Long.class);
            // Dos grupos distintos que se renombran al mismo texto a la vez: el segundo espera
            // y su plan ya ve al primero (no quedan dos cargos vigentes con el mismo nombre).
            if (d.texto() != null) {
                jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(:t))",
                        Map.of("t", "cargo:" + PlanCargos.limpio(d.texto())), Object.class);
            }
            Resultado p = plan(d);
            if (firmaVista == null || !firmaVista.equals(p.firma())) {
                throw new IllegalStateException("Los datos cambiaron desde la vista previa. Vuelva a revisarla.");
            }
            if (!p.errores().isEmpty()) throw new IllegalStateException(String.join(" ", p.errores()));
            hecho[0] = p;
            return guardarEnBase(p, d, lote, motivo, autor);
        });
        Resultado plan = hecho[0];
        Map<Long, String> estadoAntes = new HashMap<>();
        for (CambioFila c : plan.filas()) estadoAntes.put(c.fila().idResponsable(), c.fila().estadoVsiaf());

        EnvioFilasVsiaf.Resumen envio = envioFilas.enviar(porFila, estadoAntes, usuarioNombre);
        try {
            historial.guardarTodos(porFila);
            actividadService.registrar(autor, ActividadService.MOD_CARGO, ActividadService.ACC_MODIFICACION,
                    plan.texto(), resumen(plan) + (motivo == null || motivo.isBlank() ? "" : " Motivo: " + motivo.trim()),
                    Math.max(plan.filas().size(), 1), d.idElegido());
        } catch (Exception ex) {
            log.error("[CARGOS] Lote {} aplicado, pero no se pudo anotar el estado del envío: {}", lote, ex.getMessage(), ex);
        }
        String msg = "Cargos juntados en el SCIAF (" + plan.filas().size() + " oficina(s))." + envio.texto();
        return new Aplicado(envio.fallidos() == 0, msg, lote);
    }

    private List<PersonaCambio> guardarEnBase(Resultado plan, Decision d, String lote, String motivo, Usuario autor) {
        Cargo elegido = cargoDao.findById(d.idElegido())
                .orElseThrow(() -> new IllegalStateException("El cargo elegido ya no existe."));
        em.refresh(elegido);
        String nombreAntes = elegido.getNombre();
        Long idAlias = null;
        if (plan.renombra()) {
            String textoViejo = elegido.getNombre();
            elegido.setNombre(plan.texto());
            if (autor != null) elegido.setModificacionIdUsuario(autor.getIdUsuario());
            cargoDao.save(elegido);
            // El texto viejo queda como alias FUSIONADO → elegido: las filas del VSIAF que aún
            // lo traen (UPDATE en camino, filas fuera del plan, altas del VSIAF) siguen yendo al
            // elegido en la sync, y nadie lo vuelve a crear.
            Cargo alias = new Cargo();
            alias.setNombre(textoViejo);
            alias.setDescripcion("Texto anterior de «" + plan.texto() + "» (renombrado en Personas y cargos)");
            alias.setEstado(ESTADO_FUSIONADO);
            alias.setFusionadoEn(elegido.getIdCargo());
            alias.setRegistro(new java.util.Date());
            if (autor != null) alias.setRegistroIdUsuario(autor.getIdUsuario());
            idAlias = cargoDao.save(alias).getIdCargo();
        }
        List<Map<String, Object>> juntados = new ArrayList<>();
        for (Long id : plan.idsJuntar()) {
            Cargo c = cargoDao.findById(id).orElseThrow(() -> new IllegalStateException("Un cargo a juntar ya no existe."));
            em.refresh(c);
            juntados.add(Map.of("id", c.getIdCargo(), "nombre", c.getNombre() == null ? "" : c.getNombre()));
            c.setEstado(ESTADO_FUSIONADO);
            c.setFusionadoEn(elegido.getIdCargo());
            if (autor != null) c.setModificacionIdUsuario(autor.getIdUsuario());
            cargoDao.save(c);
        }
        // Registro del lote (sin persona: id 0): lo necesario para revertir desde Historial.
        PersonaCambio fusion = historial.nuevo(lote, 0L, PersonaCambio.TIPO_CARGO_FUSION, autor, ORIGEN);
        fusion.setReferencia("Cargo «" + plan.texto() + "» (id " + elegido.getIdCargo() + ")");
        Map<String, Object> antesF = new LinkedHashMap<>();
        antesF.put("idElegido", elegido.getIdCargo());
        antesF.put("nombreElegido", nombreAntes);
        antesF.put("juntados", juntados);
        Map<String, Object> despuesF = new LinkedHashMap<>();
        despuesF.put("idElegido", elegido.getIdCargo());
        despuesF.put("nombreElegido", elegido.getNombre());
        despuesF.put("idAlias", idAlias);
        fusion.setAntes(historial.json(antesF));
        fusion.setDespues(historial.json(despuesF));
        fusion.setEstadoVsiaf(PersonaCambio.VSIAF_NO_APLICA);
        fusion.setMotivo(motivo == null ? null : motivo.length() > 500 ? motivo.substring(0, 500) : motivo);
        historial.guardar(fusion);

        List<PersonaCambio> porFila = new ArrayList<>();
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        for (CambioFila cf : plan.filas()) {
            Responsable r = responsableDao.findById(cf.fila().idResponsable())
                    .orElseThrow(() -> new IllegalStateException("Una de las oficinas ya no existe."));
            if (!"ACTIVO".equals(r.getEstado())) throw new IllegalStateException("La fila " + cf.fila().referencia() + " ya no está vigente.");
            r.setCargo(elegido);
            r.setFechaUlt(LocalDate.now());
            r.setUsuario(usuarioNombre);
            if (EnvioFilasVsiaf.marcarEnCamino(cf.fila().estadoVsiaf())) r.setPendienteDbf(true);
            if (autor != null) r.setModificacionIdUsuario(autor.getIdUsuario());
            responsableDao.save(r);

            PersonaCambio c = historial.nuevo(lote, cf.fila().idPersona(), PersonaCambio.TIPO_CARGO, autor, ORIGEN);
            c.setIdResponsable(r.getIdResponsable());
            c.setReferencia(cf.fila().referencia());
            Map<String, Object> antes = new LinkedHashMap<>();
            antes.put("cargo", cf.cargoAntes() == null ? "" : cf.cargoAntes());
            antes.put("idCargo", cf.fila().idCargo());
            Map<String, Object> despues = new LinkedHashMap<>();
            despues.put("cargo", cf.cargoDespues());
            despues.put("idCargo", elegido.getIdCargo());
            c.setAntes(historial.json(antes));
            c.setDespues(historial.json(despues));
            c.setMotivo(motivo == null ? null : motivo.length() > 500 ? motivo.substring(0, 500) : motivo);
            porFila.add(c);
        }
        return historial.guardarTodos(porFila);
    }

    /** «No son el mismo cargo»: el agrupador no vuelve a juntar ninguno de estos pares. */
    public int descartar(Collection<Long> ids, Usuario autor) {
        // Decide qué se ofrece juntar: solo ADMINISTRADOR / SUPER USUARIO (D7: APOYO no decide solo).
        if (!RolesSciaf.esAdministrativo(autor)) {
            throw new IllegalStateException("Marcar cargos como distintos es para ADMINISTRADOR o SUPER USUARIO.");
        }
        List<Long> l = ids == null ? List.of() : ids.stream().distinct().sorted().toList();
        if (l.size() < 2) throw new IllegalArgumentException("Indique al menos dos cargos.");
        if (l.size() > 40) throw new IllegalArgumentException("Demasiados cargos a la vez (máximo 40).");
        Integer nuevos = tx.execute(st -> {
            int n = 0;
            for (int i = 0; i < l.size(); i++) {
                for (int j = i + 1; j < l.size(); j++) {
                    // ON CONFLICT: si otro lo marcó al mismo tiempo, no hay error.
                    n += jdbc.update("INSERT INTO cargo_no_duplicado (id_cargo_a, id_cargo_b, id_usuario, usuario, fecha)"
                            + " VALUES (:a, :b, :u, :n, now()) ON CONFLICT (id_cargo_a, id_cargo_b) DO NOTHING",
                            new MapSqlParameterSource().addValue("a", l.get(i)).addValue("b", l.get(j))
                                    .addValue("u", autor.getIdUsuario()).addValue("n", autor.getUsuario()));
                }
            }
            return n;
        });
        actividadService.registrar(autor, ActividadService.MOD_CARGO, ActividadService.ACC_MODIFICACION,
                "Cargos " + l, "Marcó como distintos los cargos " + l + " (no se volverán a sugerir juntos).", l.get(0));
        return nuevos == null ? 0 : nuevos;
    }

    // ── Solicitud de autorización (APOYO) ─────────────────────────────────

    public Map<String, Object> datosSolicitud(Decision d, String firma, String motivo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idElegido", d.idElegido());
        m.put("texto", d.texto());
        m.put("idsJuntar", d.idsJuntar() == null ? List.of() : d.idsJuntar());
        m.put("firma", firma);
        m.put("motivo", motivo);
        return m;
    }

    public static Decision decisionDe(Map<String, Object> datos) {
        return new Decision(PersonasCargosService.aLong(datos.get("idElegido")),
                datos.get("texto") == null ? null : datos.get("texto").toString(),
                PersonasCargosService.aLongs(datos.get("idsJuntar")));
    }

    /** Cargos que toca un pedido (para no tener dos solicitudes pendientes sobre el mismo). */
    public static Set<Long> cargosDe(Map<String, Object> datos) {
        Set<Long> s = new HashSet<>(PersonasCargosService.aLongs(datos.get("idsJuntar")));
        Long e = PersonasCargosService.aLong(datos.get("idElegido"));
        if (e != null) s.add(e);
        return s;
    }

    public String resumen(Resultado plan) {
        List<String> partes = new ArrayList<>();
        if (!plan.idsJuntar().isEmpty()) partes.add("junta " + plan.idsJuntar().size() + " cargo(s) en «" + plan.texto() + "»");
        if (plan.renombra()) partes.add("queda escrito «" + plan.texto() + "»");
        return (partes.isEmpty() ? "sin cambios" : String.join("; ", partes)) + " · " + plan.filas().size() + " oficina(s).";
    }

    @Override
    public Set<String> tipos() {
        return Set.of(TIPO_SOLICITUD);
    }

    /** Aprobada la solicitud: se rehace el plan y solo se aplica si nada cambió desde que se pidió. */
    @Override
    public String ejecutar(String tipo, Map<String, Object> datos, Usuario autor) {
        Object firma = datos.get("firma"), motivo = datos.get("motivo");
        return aplicar(decisionDe(datos), firma == null ? null : firma.toString(),
                motivo == null ? null : motivo.toString(), autor).msg();
    }

    // ── Conversión ────────────────────────────────────────────────────────

    private static Map<String, Object> aMapa(CargoUso c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.id());
        m.put("nombre", c.nombre());
        m.put("filas", c.filas());
        m.put("personas", c.personas());
        return m;
    }

    public static Map<String, Object> aMapa(Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("errores", r.errores());
        m.put("avisos", r.avisos());
        m.put("firma", r.firma());
        m.put("texto", r.texto());
        m.put("renombra", r.renombra());
        m.put("juntar", r.idsJuntar().size());
        m.put("filasQueCambian", r.filas().size());
        m.put("filasAlVsiaf", r.filasAlVsiaf());
        m.put("confirmacionExtra", r.filas().size() > PlanCargos.FILAS_CONFIRMACION_EXTRA);
        List<Map<String, Object>> filas = new ArrayList<>();
        for (CambioFila c : r.filas()) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("idResponsable", c.fila().idResponsable());
            f.put("referencia", c.fila().referencia());
            f.put("persona", c.fila().persona());
            f.put("cargoAntes", c.cargoAntes());
            f.put("cargoDespues", c.cargoDespues());
            f.put("enviaVsiaf", c.enviaVsiaf());
            f.put("nota", c.nota());
            filas.add(f);
        }
        m.put("filas", filas);
        return m;
    }
}
