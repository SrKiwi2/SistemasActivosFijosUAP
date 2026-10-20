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
import java.util.Objects;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.usic.SistemasActivosFijosUAP.componet.VsiafDisponibilidad;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.dao.IPersonasDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.PersonaCambio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.CambioFila;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.DatosPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Decision;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Fila;
import com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.Resultado;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.EjecutorAutorizacion;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService;

/**
 * «Personas y cargos» (docs/PLAN_PERSONAS_UNIFICADAS.md): ficha de una persona con todas
 * sus filas de RESP, cambio de cargo en varias filas a la vez y unificación de la misma
 * persona registrada varias veces. Cada cambio queda en {@link PersonaCambio} y viaja al
 * VSIAF por la cola, fila por fila.
 * <p>
 * Quién puede hacerlo lo decide el controlador: ADMINISTRADOR / SUPER USUARIO aplican
 * directo; un usuario con la opción asignada (APOYO) deja una solicitud de autorización,
 * que al aprobarse pasa por {@link #ejecutar}.
 */
@Service
public class PersonasCargosService implements EjecutorAutorizacion {

    private static final Logger log = LoggerFactory.getLogger(PersonasCargosService.class);

    public static final String TIPO_SOLICITUD = "PERSONA_CAMBIO";
    public static final String TIPO_SEPARAR = "PERSONA_SEPARAR";
    public static final String MODULO_SOLICITUD = "PERSONA";
    public static final String ESTADO_FUSIONADA = ResolutorPersonaSync.ESTADO_FUSIONADA;

    private final NamedParameterJdbcTemplate jdbc;
    private final IPersonasDao personaDao;
    private final IResposableDao responsableDao;
    private final ResponsableAltaService responsableAltaService;
    private final ResponsableGestionService responsableGestionService;
    private final VsiafApoyoService vsiafApoyoService;
    private final ActividadService actividadService;
    private final PersonaHistorialService historial;
    private final TransactionTemplate tx;
    private final VsiafDisponibilidad vsiaf;
    private final EnvioFilasVsiaf envioFilas;
    @PersistenceContext
    private EntityManager em;

    public PersonasCargosService(@Qualifier("pgJdbcTemplate") JdbcTemplate jdbcTemplate, IPersonasDao personaDao,
                                 IResposableDao responsableDao, ResponsableAltaService responsableAltaService,
                                 ResponsableGestionService responsableGestionService,
                                 VsiafApoyoService vsiafApoyoService, ActividadService actividadService,
                                 PersonaHistorialService historial, PlatformTransactionManager txManager,
                                 VsiafDisponibilidad vsiaf, EnvioFilasVsiaf envioFilas) {
        this.vsiaf = vsiaf;
        this.envioFilas = envioFilas;
        this.jdbc = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.personaDao = personaDao;
        this.responsableDao = responsableDao;
        this.responsableAltaService = responsableAltaService;
        this.responsableGestionService = responsableGestionService;
        this.vsiafApoyoService = vsiafApoyoService;
        this.actividadService = actividadService;
        this.historial = historial;
        this.tx = new TransactionTemplate(txManager);
    }

    // ── Lectura ───────────────────────────────────────────────────────────

    /** Buscador de personas por C.I. o nombre (palabras en cualquier orden). */
    public List<Map<String, Object>> buscar(String q, Collection<Long> excluir) {
        String t = q == null ? "" : q.trim().toUpperCase();
        if (t.length() < 2) return List.of();
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder();
        String[] palabras = t.split("\\s+");
        for (int i = 0; i < palabras.length && i < 5; i++) {
            where.append(" AND upper(concat_ws(' ', pe.nombre, pe.paterno, pe.materno, pe.ci)) LIKE :w").append(i);
            p.addValue("w" + i, "%" + palabras[i] + "%");
        }
        List<Long> ex = excluir == null ? List.of() : new ArrayList<>(excluir);
        if (!ex.isEmpty()) {
            where.append(" AND pe.id_persona NOT IN (:ex)");
            p.addValue("ex", ex);
        }
        String sql = """
                SELECT pe.id_persona, pe.nombre, pe.paterno, pe.materno, pe.ci,
                       (SELECT count(*) FROM responsable r WHERE r.id_persona = pe.id_persona AND r._estado = 'ACTIVO') AS oficinas,
                       (SELECT count(*) FROM responsable r JOIN activo a ON a.id_responsable = r.id_responsable
                         WHERE r.id_persona = pe.id_persona AND r._estado = 'ACTIVO' AND a._estado = 'ACTIVO') AS bienes,
                       EXISTS (SELECT 1 FROM usuario u WHERE u.id_persona = pe.id_persona) AS tiene_usuario
                  FROM persona pe
                 WHERE pe._estado = 'ACTIVO'""" + where + """

                 ORDER BY pe.paterno NULLS LAST, pe.materno NULLS LAST, pe.nombre
                 LIMIT 25""";
        return jdbc.query(sql, p, (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", rs.getLong("id_persona"));
            m.put("nombre", unir(rs.getString("nombre"), rs.getString("paterno"), rs.getString("materno")));
            m.put("ci", rs.getString("ci"));
            m.put("oficinas", rs.getLong("oficinas"));
            m.put("bienes", rs.getLong("bienes"));
            m.put("tieneUsuario", rs.getBoolean("tiene_usuario"));
            return m;
        });
    }

    /**
     * Ficha: la principal, las que se le suman, todas sus filas vigentes (con su estado en
     * el VSIAF) y el historial (también el de las personas ya unificadas en ella).
     */
    public Map<String, Object> ficha(Long idPrincipal, List<Long> idsSecundarias) {
        Datos datos = cargar(idPrincipal, idsSecundarias);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("principal", aMapa(datos.principal));
        m.put("secundarias", datos.secundarias.stream().map(PersonasCargosService::aMapa).toList());
        m.put("filas", datos.filas.stream().map(PersonasCargosService::aMapa).toList());
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(idPrincipal);
        ids.addAll(idsUnificadasEn(idPrincipal));
        m.put("historial", historial.deLasPersonas(ids, 150));
        m.put("unificadas", idsUnificadasEn(idPrincipal).size());
        return m;
    }

    /** Plan (vista previa): no guarda nada. */
    public Resultado plan(Decision d) {
        Datos datos = cargar(d.idPrincipal(), d.idsSecundarias());
        Set<String> invalidos = responsableGestionService.ciInvalidos();
        String ciFinal = d.ci() != null ? d.ci() : datos.principal.ci();
        String numero = IdentidadPersona.numeroCi(ciFinal, invalidos);
        Resultado r = PlanPersona.calcular(datos.principal, datos.secundarias, datos.filas, d, invalidos,
                numero == null ? List.of() : personasConNumeroCi(numero));
        // uk_resp_custodia_persona (id_oficina, id_persona) WHERE es_custodia no mira el estado:
        // una custodia ELIMINADA de la principal en la misma oficina también choca.
        Set<Long> oficinasQueLlegan = new HashSet<>();
        for (CambioFila c : r.filas()) {
            if (c.cambiaPersona() && c.fila().esCustodia() && c.fila().idOficina() != null) oficinasQueLlegan.add(c.fila().idOficina());
        }
        if (!oficinasQueLlegan.isEmpty()) {
            Long conflictos = jdbc.queryForObject(
                    "SELECT count(*) FROM responsable WHERE es_custodia AND id_persona = :p AND id_oficina IN (:o)",
                    new MapSqlParameterSource().addValue("p", d.idPrincipal()).addValue("o", oficinasQueLlegan), Long.class);
            if (conflictos != null && conflictos > 0 && r.errores().stream().noneMatch(e -> e.contains("custodias"))) {
                r.errores().add("La principal ya es (o fue) custodia en la misma oficina de faltantes que una de las "
                        + "sumadas: no se pueden unificar todavía (habría que juntar primero sus custodias).");
            }
        }
        return r;
    }

    // ── Escritura ─────────────────────────────────────────────────────────

    public record Aplicado(boolean ok, String msg, String lote, int filas, int enviados, int fallidos, int sinEnviar) {
    }

    /**
     * Aplicación directa: solo ADMINISTRADOR / SUPER USUARIO. Se revisa aquí además de en el
     * controlador (/administracion/** es permitAll): cualquier otra entrada a este servicio
     * queda cubierta.
     */
    public Aplicado aplicarDirecto(Decision d, String firmaVista, String motivo, Usuario autor) {
        if (!RolesSciaf.esAdministrativo(autor)) {
            throw new IllegalStateException("Solo ADMINISTRADOR o SUPER USUARIO aplican directo; el resto envía una solicitud.");
        }
        return aplicar(d, firmaVista, motivo, autor, null);
    }

    /**
     * Aplica el plan si la firma coincide con la que vio el usuario (nadie cambió nada
     * entretanto). Dentro de una transacción bloquea a las personas, rehace el plan y compara
     * la firma; guarda todo; después del commit manda cada fila al VSIAF.
     *
     * @throws IllegalStateException si cambió algo desde la vista previa o hay errores
     */
    private Aplicado aplicar(Decision d, String firmaVista, String motivo, Usuario autor, Long idSolicitud) {
        // También al aprobar una solicitud: sin VSIAF (localhost) se guardaría en la base pero
        // cada envío fallaría y dejaría las filas como pendientes en producción.
        if (!vsiaf.dbf("Personas y cargos")) {
            throw new IllegalStateException("No se aplica desde aquí: " + vsiaf.motivoDbf()
                    + " Hágalo desde el servidor del SCIAF.");
        }
        String lote = PersonaHistorialService.nuevoLote();
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        Resultado[] hecho = new Resultado[1];
        List<PersonaCambio> porFila = tx.execute(st -> {
            // Bloqueo de las personas (en orden de id: dos pedidos cruzados no se traban) y plan
            // recalculado con lo que hay ahora: dos aplicaciones a la vez no pasan las dos.
            List<Long> ids = new ArrayList<>();
            ids.add(d.idPrincipal());
            if (d.idsSecundarias() != null) ids.addAll(d.idsSecundarias());
            jdbc.queryForList("SELECT id_persona FROM persona WHERE id_persona IN (:ids) ORDER BY id_persona FOR UPDATE",
                    Map.of("ids", ids), Long.class);
            if (d.ci() != null && IdentidadPersona.numeroCi(d.ci(), Set.of()) != null) {
                // Mismo candado que separar(): dos personas con el mismo C.I. no nacen a la vez.
                jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(:t))",
                        Map.of("t", "persona-ci:" + IdentidadPersona.numeroCi(d.ci(), Set.of())), Object.class);
            }
            // Y sus oficinas: una reversión u otro cambio sobre ellas espera (Responsable no tiene @Version).
            jdbc.queryForList("SELECT id_responsable FROM responsable WHERE id_persona IN (:ids) AND _estado = 'ACTIVO'"
                    + " ORDER BY id_responsable FOR UPDATE", Map.of("ids", ids), Long.class);
            Resultado p = plan(d);
            if (firmaVista == null || !firmaVista.equals(p.firma())) {
                throw new IllegalStateException("Los datos cambiaron desde la vista previa (alguien modificó a estas "
                        + "personas o sus oficinas). Vuelva a cargar la ficha y revise la vista previa.");
            }
            if (!p.errores().isEmpty()) throw new IllegalStateException(String.join(" ", p.errores()));
            if ((p.ciEnConflicto() || !p.idsSecundarias().isEmpty()) && (motivo == null || motivo.isBlank())) {
                throw new IllegalArgumentException("Indique el motivo: queda en el historial.");
            }
            hecho[0] = p;
            return guardarEnBase(p, lote, motivo, autor, idSolicitud);
        });
        Resultado plan = hecho[0];
        Map<Long, String> estadoAntes = new HashMap<>();
        for (CambioFila cf : plan.filas()) estadoAntes.put(cf.fila().idResponsable(), cf.fila().estadoVsiaf());

        // Al VSIAF, después del commit: una orden por fila (el worker las aplica en orden).
        EnvioFilasVsiaf.Resumen envio = envioFilas.enviar(porFila, estadoAntes, usuarioNombre);
        int filas = (int) plan.filasQueCambian();
        // Lo de abajo es solo registro: la base ya se confirmó y las órdenes ya están en la cola.
        // Si falla, no hay que decir «no se aplicó nada».
        try {
            historial.guardarTodos(porFila);
            String resumen = resumen(plan) + (motivo == null || motivo.isBlank() ? "" : " Motivo: " + motivo.trim());
            actividadService.registrar(autor, ActividadService.MOD_PERSONA, ActividadService.ACC_MODIFICACION,
                    plan.nuevaPersona().nombreCompleto(), resumen, Math.max(filas, 1), plan.nuevaPersona().id());
        } catch (Exception ex) {
            log.error("[PERSONAS] Lote {} aplicado, pero no se pudo anotar el estado del envío: {}", lote, ex.getMessage(), ex);
        }

        String msg = "Cambios guardados en el SCIAF (" + filas + " oficina(s))." + envio.texto();
        return new Aplicado(envio.fallidos() == 0, msg, lote, filas, envio.enviados(), envio.fallidos(), envio.sinEnviar());
    }

    /** Todo lo de la base, en una transacción. Devuelve una entrada de historial por fila. */
    private List<PersonaCambio> guardarEnBase(Resultado plan, String lote, String motivo, Usuario autor, Long idSolicitud) {
        DatosPersona nueva = plan.nuevaPersona();
        Persona principal = personaDao.findById(nueva.id())
                .orElseThrow(() -> new IllegalStateException("La persona principal ya no existe."));
        em.refresh(principal);   // la sesión del pedido puede traerla de antes del bloqueo
        Map<String, Object> fotoPrincipal = PersonaHistorialService.foto(principal);
        List<PersonaCambio> otros = new ArrayList<>();
        String origen = PersonaHistorialService.ORIGEN_PERSONAS_CARGOS;

        // 1. Datos de la principal.
        if (plan.cambiaDatosPersona()) {
            principal.setNombre(nueva.nombre());
            principal.setPaterno(nueva.paterno());
            principal.setMaterno(nueva.materno());
            principal.setCi(nueva.ci());
            if (autor != null) principal.setModificacionIdUsuario(autor.getIdUsuario());
            principal = personaDao.save(principal);
            PersonaCambio c = historial.nuevo(lote, principal.getIdPersona(), PersonaCambio.TIPO_DATOS, autor, origen);
            c.setReferencia(nueva.nombreCompleto());
            c.setAntes(historial.json(fotoPrincipal));
            c.setDespues(historial.json(PersonaHistorialService.foto(principal)));
            otros.add(c);
        }

        // 2. Las que se suman enteras: quedan FUSIONADAS (no se borran) y lo suyo pasa a la
        //    principal. Las que conservan alguna fila («no es de esta persona») siguen vigentes.
        for (Long idSec : plan.idsFusionar()) {
            Persona s = personaDao.findById(idSec)
                    .orElseThrow(() -> new IllegalStateException("Una de las personas a sumar ya no existe."));
            em.refresh(s);
            Map<String, Object> fotoSec = PersonaHistorialService.foto(s);
            s.setEstado(ESTADO_FUSIONADA);
            s.setFusionadaEn(principal.getIdPersona());
            if (autor != null) s.setModificacionIdUsuario(autor.getIdUsuario());
            personaDao.save(s);
            // Qué se mueve exactamente: la reversión (Historial) lo devuelve uno por uno.
            List<Long> actas = jdbc.queryForList("SELECT id_acta FROM acta_faltante WHERE id_persona = :s",
                    Map.of("s", idSec), Long.class);
            List<Long> usuarios = jdbc.queryForList("SELECT id_usuario FROM usuario WHERE id_persona = :s",
                    Map.of("s", idSec), Long.class);
            jdbc.update("UPDATE acta_faltante SET id_persona = :p WHERE id_persona = :s",
                    new MapSqlParameterSource().addValue("p", principal.getIdPersona()).addValue("s", idSec));
            jdbc.update("UPDATE usuario SET id_persona = :p WHERE id_persona = :s",
                    new MapSqlParameterSource().addValue("p", principal.getIdPersona()).addValue("s", idSec));
            PersonaCambio c = historial.nuevo(lote, principal.getIdPersona(), PersonaCambio.TIPO_FUSION_PERSONA, autor, origen);
            c.setReferencia(unir(s.getNombre(), s.getPaterno(), s.getMaterno()) + " (id " + idSec + ") → id "
                    + principal.getIdPersona());
            Map<String, Object> despues = new LinkedHashMap<>();
            despues.put("estado", ESTADO_FUSIONADA);
            despues.put("fusionadaEn", principal.getIdPersona());
            despues.put("actas", actas);
            despues.put("usuarios", usuarios);
            c.setAntes(historial.json(fotoSec));
            c.setDespues(historial.json(despues));
            c.setEstadoVsiaf(PersonaCambio.VSIAF_NO_APLICA);
            otros.add(c);
        }

        // 3. Filas: persona y cargo (nunca oficina, código ni bienes).
        List<PersonaCambio> porFila = new ArrayList<>();
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        for (CambioFila cf : plan.filas()) {
            if (!cf.hayCambio()) continue;
            Responsable r = responsableDao.findById(cf.fila().idResponsable())
                    .orElseThrow(() -> new IllegalStateException("Una de las oficinas ya no existe."));
            if (!"ACTIVO".equals(r.getEstado())) throw new IllegalStateException("La fila " + cf.fila().referencia() + " ya no está vigente.");
            Long idCargoAntes = r.getCargo() == null ? null : r.getCargo().getIdCargo();
            if (cf.cambiaPersona()) r.setPersona(principal);
            if (cf.cambiaCargo()) r.setCargo(responsableAltaService.obtenerCargo(cf.cargoDespues(), autor));
            r.setFechaUlt(LocalDate.now());
            r.setUsuario(usuarioNombre);
            // Pendiente mientras su UPDATE viaja: la sync no la pisa con el dato viejo del DBF.
            if (EnvioFilasVsiaf.marcarEnCamino(cf.fila().estadoVsiaf())) r.setPendienteDbf(true);
            if (autor != null) r.setModificacionIdUsuario(autor.getIdUsuario());
            responsableDao.save(r);

            List<String> tipos = new ArrayList<>();
            if (cf.cambiaPersona()) tipos.add(PersonaCambio.TIPO_FUSION_FILA);
            if (cf.cambiaDatos()) tipos.add(PersonaCambio.TIPO_DATOS);
            if (cf.cambiaCargo()) tipos.add(PersonaCambio.TIPO_CARGO);
            PersonaCambio c = historial.nuevo(lote, principal.getIdPersona(), String.join("+", tipos), autor, origen);
            c.setIdResponsable(r.getIdResponsable());
            c.setReferencia(cf.fila().referencia());
            Long idCargoDespues = r.getCargo() == null ? null : r.getCargo().getIdCargo();
            c.setAntes(historial.json(filaJson(cf.idPersonaAntes(), cf.nombreAntes(), cf.ciAntes(), cf.cargoAntes(), idCargoAntes)));
            c.setDespues(historial.json(filaJson(principal.getIdPersona(), cf.nombreDespues(), cf.ciDespues(), cf.cargoDespues(), idCargoDespues)));
            porFila.add(c);
        }

        for (PersonaCambio c : otros) {
            c.setMotivo(recortar(motivo, 500));
            c.setIdSolicitud(idSolicitud);
        }
        for (PersonaCambio c : porFila) {
            c.setMotivo(recortar(motivo, 500));
            c.setIdSolicitud(idSolicitud);
        }
        historial.guardarTodos(otros);
        return historial.guardarTodos(porFila);
    }

    // ── Pasar oficinas a otra persona (fase 6) ────────────────────────────

    /** Vista previa: no guarda nada. */
    public PlanSeparacion.Resultado planSeparacion(PlanSeparacion.Decision d) {
        if (d.idOrigen() == null) throw new IllegalArgumentException("Elija la persona de origen.");
        Datos origen = cargar(d.idOrigen(), List.of());
        DatosPersona destino = null;
        Set<Long> custodia = Set.of();
        if (d.idDestino() != null) {
            destino = cargar(d.idDestino(), List.of()).principal();
            custodia = new HashSet<>(jdbc.queryForList(
                    "SELECT DISTINCT id_oficina FROM responsable WHERE es_custodia AND id_persona = :p",
                    Map.of("p", d.idDestino()), Long.class));
        }
        Set<String> invalidos = responsableGestionService.ciInvalidos();
        List<Long> conCi = List.of();
        List<Long> homonimos = List.of();
        if (d.idDestino() == null && d.nueva() != null) {
            String numero = IdentidadPersona.numeroCi(d.nueva().ci(), invalidos);
            if (numero != null) {
                // Vigentes con ese C.I., y las unificadas con ese C.I. → la persona en la que quedaron.
                conCi = jdbc.queryForList("SELECT DISTINCT CASE WHEN _estado = 'FUSIONADA' THEN coalesce(fusionada_en, id_persona)"
                        + " ELSE id_persona END FROM persona WHERE _estado IN ('ACTIVO', 'FUSIONADA')"
                        + " AND substring(regexp_replace(split_part(coalesce(ci, ''), '-', 1), '[.\\s]', '', 'g') from '^[0-9]+') = :n",
                        Map.of("n", numero), Long.class);
            }
            homonimos = homonimos(d.nueva());
        }
        return PlanSeparacion.calcular(d, origen.principal(), origen.filas(), destino, invalidos, conCi, custodia, homonimos);
    }

    /** Personas vigentes que se llaman igual (normalizado, con la Ñ equivalente) que la persona nueva. */
    private List<Long> homonimos(PlanSeparacion.Nueva n) {
        String nombre = IdentidadPersona.normalizarNombre(String.join(" ", nvlS(n.nombre()), nvlS(n.paterno()), nvlS(n.materno())));
        if (nombre.isEmpty()) return List.of();
        String pista = n.paterno() != null && !n.paterno().isBlank() ? n.paterno().trim().toUpperCase() : n.nombre().trim().toUpperCase();
        List<Long> out = new ArrayList<>();
        jdbc.query("SELECT id_persona, nombre, paterno, materno FROM persona WHERE _estado = 'ACTIVO'"
                + " AND upper(concat_ws(' ', nombre, paterno, materno)) LIKE :q", Map.of("q", "%" + pista + "%"), rs -> {
                    String otro = IdentidadPersona.normalizarNombre(String.join(" ", nvlS(rs.getString("nombre")),
                            nvlS(rs.getString("paterno")), nvlS(rs.getString("materno"))));
                    if (IdentidadPersona.nombresEquivalentes(otro, nombre)) out.add(rs.getLong("id_persona"));
                });
        return out;
    }

    private static String nvlS(String s) {
        return s == null ? "" : s;
    }

    /** Aplicación directa: solo ADMINISTRADOR / SUPER USUARIO. */
    public Aplicado separarDirecto(PlanSeparacion.Decision d, String firma, String motivo, Usuario autor) {
        if (!RolesSciaf.esAdministrativo(autor)) {
            throw new IllegalStateException("Solo ADMINISTRADOR o SUPER USUARIO aplican directo; el resto envía una solicitud.");
        }
        return separar(d, firma, motivo, autor);
    }

    private Aplicado separar(PlanSeparacion.Decision d, String firmaVista, String motivo, Usuario autor) {
        if (!vsiaf.dbf("Personas y cargos")) {
            throw new IllegalStateException("No se aplica desde aquí: " + vsiaf.motivoDbf() + " Hágalo desde el servidor del SCIAF.");
        }
        if (motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Indique el motivo: queda en el historial.");
        String lote = PersonaHistorialService.nuevoLote();
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        PlanSeparacion.Resultado[] hecho = new PlanSeparacion.Resultado[1];
        List<PersonaCambio> porFila = tx.execute(st -> {
            List<Long> ids = new ArrayList<>();
            ids.add(d.idOrigen());
            if (d.idDestino() != null) ids.add(d.idDestino());
            jdbc.queryForList("SELECT id_persona FROM persona WHERE id_persona IN (:ids) ORDER BY id_persona FOR UPDATE",
                    Map.of("ids", ids), Long.class);
            jdbc.queryForList("SELECT id_responsable FROM responsable WHERE id_persona IN (:ids) AND _estado = 'ACTIVO'"
                    + " ORDER BY id_responsable FOR UPDATE", Map.of("ids", ids), Long.class);
            if (d.idDestino() == null && d.nueva() != null) {
                // Dos personas nuevas con el mismo C.I. a la vez: la segunda espera y ya ve a la primera.
                jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext(:t))",
                        Map.of("t", "persona-ci:" + String.valueOf(IdentidadPersona.numeroCi(d.nueva().ci(), Set.of()))), Object.class);
            }
            PlanSeparacion.Resultado p = planSeparacion(d);
            if (firmaVista == null || !firmaVista.equals(p.firma())) {
                throw new IllegalStateException("Los datos cambiaron desde la vista previa. Vuelva a revisarla.");
            }
            if (!p.errores().isEmpty()) throw new IllegalStateException(String.join(" ", p.errores()));
            hecho[0] = p;
            return guardarSeparacion(p, d, lote, motivo, autor);
        });
        PlanSeparacion.Resultado plan = hecho[0];
        Map<Long, String> estadoAntes = new HashMap<>();
        for (PlanSeparacion.CambioFila c : plan.filas()) estadoAntes.put(c.fila().idResponsable(), c.fila().estadoVsiaf());
        EnvioFilasVsiaf.Resumen envio = envioFilas.enviar(porFila, estadoAntes, usuarioNombre);
        try {
            historial.guardarTodos(porFila);
            actividadService.registrar(autor, ActividadService.MOD_PERSONA, ActividadService.ACC_MODIFICACION,
                    plan.destino().nombreCompleto(), resumenSeparacion(plan) + " Motivo: " + motivo.trim(),
                    Math.max(plan.filas().size(), 1), d.idOrigen());
        } catch (Exception ex) {
            log.error("[PERSONAS] Separación {} aplicada, pero no se pudo anotar el estado del envío: {}", lote, ex.getMessage(), ex);
        }
        String msg = "Oficinas pasadas en el SCIAF (" + plan.filas().size() + ")." + envio.texto();
        return new Aplicado(envio.fallidos() == 0, msg, lote, plan.filas().size(), envio.enviados(), envio.fallidos(), envio.sinEnviar());
    }

    private List<PersonaCambio> guardarSeparacion(PlanSeparacion.Resultado plan, PlanSeparacion.Decision d, String lote,
                                                  String motivo, Usuario autor) {
        String origen = PersonaHistorialService.ORIGEN_PERSONAS_CARGOS;
        String mot = recortar(motivo, 500);
        Persona destino;
        if (plan.destinoNuevo()) {
            DatosPersona n = plan.destino();
            destino = new Persona();
            destino.setNombre(n.nombre());
            destino.setPaterno(n.paterno());
            destino.setMaterno(n.materno());
            destino.setCi(n.ci());
            destino.setEstado("ACTIVO");
            if (autor != null) destino.setRegistroIdUsuario(autor.getIdUsuario());
            destino = personaDao.save(destino);
            PersonaCambio c = historial.nuevo(lote, destino.getIdPersona(), PersonaCambio.TIPO_PERSONA_NUEVA, autor, origen);
            c.setReferencia(recortar("Persona nueva: " + n.nombreCompleto() + (n.ci() == null ? "" : " · C.I. " + n.ci()), 150));
            c.setAntes(historial.json(Map.of()));
            c.setDespues(historial.json(PersonaHistorialService.foto(destino)));
            c.setEstadoVsiaf(PersonaCambio.VSIAF_NO_APLICA);
            c.setMotivo(mot);
            historial.guardar(c);
        } else {
            destino = personaDao.findById(d.idDestino()).orElseThrow(() -> new IllegalStateException("El destino ya no existe."));
        }

        List<PersonaCambio> porFila = new ArrayList<>();
        String usuarioNombre = autor != null ? autor.getUsuario() : "SISTEMA";
        for (PlanSeparacion.CambioFila cf : plan.filas()) {
            Responsable r = responsableDao.findById(cf.fila().idResponsable())
                    .orElseThrow(() -> new IllegalStateException("Una de las oficinas ya no existe."));
            if (!"ACTIVO".equals(r.getEstado())) throw new IllegalStateException("La fila " + cf.fila().referencia() + " ya no está vigente.");
            Long idCargo = r.getCargo() == null ? null : r.getCargo().getIdCargo();
            r.setPersona(destino);
            r.setFechaUlt(LocalDate.now());
            r.setUsuario(usuarioNombre);
            if (EnvioFilasVsiaf.marcarEnCamino(cf.fila().estadoVsiaf())) r.setPendienteDbf(true);
            if (autor != null) r.setModificacionIdUsuario(autor.getIdUsuario());
            responsableDao.save(r);

            PersonaCambio c = historial.nuevo(lote, destino.getIdPersona(), PersonaCambio.TIPO_SEPARACION, autor, origen);
            c.setIdResponsable(r.getIdResponsable());
            c.setReferencia(cf.fila().referencia());
            String cargo = cf.fila().cargo();
            c.setAntes(historial.json(filaJson(d.idOrigen(), cf.nombreAntes(), cf.ciAntes(), cargo, idCargo)));
            c.setDespues(historial.json(filaJson(destino.getIdPersona(), cf.nombreDespues(), cf.ciDespues(), cargo, idCargo)));
            c.setMotivo(mot);
            porFila.add(c);
        }
        return historial.guardarTodos(porFila);
    }

    public String resumenSeparacion(PlanSeparacion.Resultado p) {
        return p.filas().size() + " oficina(s) pasan a " + (p.destinoNuevo() ? "una persona nueva: " : "")
                + (p.destino() == null ? "?" : p.destino().nombreCompleto())
                + (p.destino() != null && p.destino().ci() != null ? " (C.I. " + p.destino().ci() + ")" : "") + ".";
    }

    public static PlanSeparacion.Decision decisionSeparacionDe(Map<String, Object> datos) {
        PlanSeparacion.Nueva nueva = null;
        if (datos.get("nueva") instanceof Map<?, ?> n) {
            nueva = new PlanSeparacion.Nueva(str(n.get("nombre")), str(n.get("paterno")), str(n.get("materno")), str(n.get("ci")));
        }
        return new PlanSeparacion.Decision(aLong(datos.get("idOrigen")), aLongs(datos.get("idsFilas")),
                aLong(datos.get("idDestino")), nueva);
    }

    public Map<String, Object> datosSeparacion(PlanSeparacion.Decision d, String firma, String motivo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idOrigen", d.idOrigen());
        m.put("idsFilas", d.idsFilas() == null ? List.of() : d.idsFilas());
        m.put("idDestino", d.idDestino());
        if (d.nueva() != null) {
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("nombre", d.nueva().nombre());
            n.put("paterno", d.nueva().paterno());
            n.put("materno", d.nueva().materno());
            n.put("ci", d.nueva().ci());
            m.put("nueva", n);
        }
        m.put("firma", firma);
        m.put("motivo", motivo);
        return m;
    }

    public static Map<String, Object> aMapa(PlanSeparacion.Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("errores", r.errores());
        m.put("avisos", r.avisos());
        m.put("firma", r.firma());
        m.put("destinoNuevo", r.destinoNuevo());
        m.put("destino", r.destino() == null ? null : r.destino().nombreCompleto());
        m.put("filasAlVsiaf", r.filasAlVsiaf());
        List<Map<String, Object>> filas = new ArrayList<>();
        for (PlanSeparacion.CambioFila c : r.filas()) {
            Map<String, Object> f = aMapa(c.fila());
            f.put("nombreAntes", c.nombreAntes());
            f.put("ciAntes", c.ciAntes());
            f.put("nombreDespues", c.nombreDespues());
            f.put("ciDespues", c.ciDespues());
            f.put("enviaVsiaf", c.enviaVsiaf());
            f.put("nota", c.nota());
            filas.add(f);
        }
        m.put("filas", filas);
        return m;
    }

    // ── Solicitud de autorización (APOYO) ─────────────────────────────────

    /** Datos que se guardan en la solicitud: todo lo necesario para rehacer el plan. */
    public Map<String, Object> datosSolicitud(Decision d, String firma, String motivo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idPrincipal", d.idPrincipal());
        m.put("idsSecundarias", d.idsSecundarias() == null ? List.of() : d.idsSecundarias());
        m.put("nombre", d.nombre());
        m.put("paterno", d.paterno());
        m.put("materno", d.materno());
        m.put("ci", d.ci());
        m.put("cargo", d.cargo());
        m.put("idsFilasCargo", d.idsFilasCargo() == null ? List.of() : d.idsFilasCargo());
        m.put("idsFilasNoSumar", d.idsFilasNoSumar() == null ? List.of() : d.idsFilasNoSumar());
        m.put("firma", firma);
        m.put("motivo", motivo);
        return m;
    }

    /** Personas que toca un pedido (para no tener dos solicitudes pendientes sobre la misma). */
    public static Set<Long> personasDe(Map<String, Object> datos) {
        Set<Long> s = new HashSet<>();
        Long p = aLong(datos.get("idPrincipal"));
        if (p != null) s.add(p);
        s.addAll(aLongs(datos.get("idsSecundarias")));
        // Separaciones (fase 6): origen y destino.
        Long o = aLong(datos.get("idOrigen")), d = aLong(datos.get("idDestino"));
        if (o != null) s.add(o);
        if (d != null) s.add(d);
        return s;
    }

    public static Decision decisionDe(Map<String, Object> datos) {
        return new Decision(aLong(datos.get("idPrincipal")), aLongs(datos.get("idsSecundarias")),
                str(datos.get("nombre")), str(datos.get("paterno")), str(datos.get("materno")), str(datos.get("ci")),
                str(datos.get("cargo")), aLongs(datos.get("idsFilasCargo")), aLongs(datos.get("idsFilasNoSumar")));
    }

    public String resumen(Resultado plan) {
        List<String> partes = new ArrayList<>();
        if (!plan.idsFusionar().isEmpty()) {
            partes.add("une " + plan.idsFusionar().size() + " registro(s) más de la persona");
        }
        long parciales = plan.idsSecundarias().size() - plan.idsFusionar().size();
        if (parciales > 0) partes.add("toma oficinas de " + parciales + " registro(s) que siguen aparte");
        if (plan.cambiaDatosPersona()) partes.add("nombre / C.I.: " + PlanPersona.nomresp(plan.nuevaPersona())
                + (plan.nuevaPersona().ci() == null ? "" : " · " + plan.nuevaPersona().ci()));
        long cargos = plan.filas().stream().filter(CambioFila::cambiaCargo).count();
        if (cargos > 0) partes.add("cargo «" + plan.cargo() + "» en " + cargos + " oficina(s)");
        return plan.nuevaPersona().nombreCompleto() + ": " + (partes.isEmpty() ? "sin cambios" : String.join("; ", partes)) + ".";
    }

    @Override
    public Set<String> tipos() {
        return Set.of(TIPO_SOLICITUD, TIPO_SEPARAR);
    }

    /** Aprobada la solicitud: se rehace el plan y solo se aplica si nada cambió desde que se pidió. */
    @Override
    public String ejecutar(String tipo, Map<String, Object> datos, Usuario autor) {
        // Ya lo aprobó el revisor: aplica el pedido de quien lo solicitó (aunque no sea administrativo).
        if (TIPO_SEPARAR.equals(tipo)) {
            return separar(decisionSeparacionDe(datos), str(datos.get("firma")), str(datos.get("motivo")), autor).msg();
        }
        Aplicado a = aplicar(decisionDe(datos), str(datos.get("firma")), str(datos.get("motivo")), autor, null);
        return a.msg();
    }

    // ── Carga de datos ────────────────────────────────────────────────────

    private record Datos(DatosPersona principal, List<DatosPersona> secundarias, List<Fila> filas) {
    }

    private Datos cargar(Long idPrincipal, List<Long> idsSecundarias) {
        if (idPrincipal == null) throw new IllegalArgumentException("Elija la persona principal.");
        List<Long> sec = idsSecundarias == null ? List.of()
                : idsSecundarias.stream().filter(Objects::nonNull).filter(id -> !id.equals(idPrincipal)).distinct().toList();
        List<Long> todas = new ArrayList<>();
        todas.add(idPrincipal);
        todas.addAll(sec);

        Set<Long> conUsuario = new HashSet<>(jdbc.queryForList(
                "SELECT id_persona FROM usuario WHERE id_persona IN (:ids)", Map.of("ids", todas), Long.class));
        // Por JDBC y no por JPA: dentro de aplicar() se lee después del bloqueo, y la sesión
        // del pedido podría devolver una copia anterior.
        Map<Long, DatosPersona> personas = new HashMap<>();
        jdbc.query("SELECT id_persona, nombre, paterno, materno, ci, _estado FROM persona WHERE id_persona IN (:ids)",
                Map.of("ids", todas), rs -> {
                    long id = rs.getLong("id_persona");
                    personas.put(id, new DatosPersona(id, rs.getString("nombre"), rs.getString("paterno"),
                            rs.getString("materno"), rs.getString("ci"), rs.getString("_estado"), conUsuario.contains(id)));
                });
        DatosPersona principal = personas.get(idPrincipal);
        if (principal == null) throw new IllegalArgumentException("La persona no existe.");
        List<DatosPersona> secundarias = new ArrayList<>();
        for (Long id : sec) {
            DatosPersona s = personas.get(id);
            if (s == null) throw new IllegalArgumentException("Una de las personas a sumar no existe (id " + id + ").");
            secundarias.add(s);
        }
        return new Datos(principal, secundarias, filas(todas));
    }

    private List<Fila> filas(List<Long> idsPersona) {
        String sql = """
                SELECT r.id_responsable, r.id_persona, r.id_oficina, pr.unidad, o.cod_ofi, r.codigo_funcionario,
                       o.nombre AS oficina, c.nombre AS cargo, r.es_custodia, r.pendiente_dbf,
                       (SELECT count(*) FROM activo a WHERE a.id_responsable = r.id_responsable AND a._estado = 'ACTIVO') AS bienes
                  FROM responsable r
                  JOIN oficina o ON o.id_oficina = r.id_oficina
                  JOIN predio pr ON pr.id_predio = o.id_predio
                  LEFT JOIN cargo c ON c.id_cargo = r.id_cargo
                 WHERE r._estado = 'ACTIVO' AND r.id_persona IN (:ids)""";
        List<Object[]> crudas = jdbc.query(sql, Map.of("ids", idsPersona), (rs, i) -> new Object[] {
                rs.getLong("id_responsable"), rs.getLong("id_persona"), rs.getLong("id_oficina"),
                rs.getString("unidad"), rs.getObject("cod_ofi") == null ? null : rs.getShort("cod_ofi"),
                rs.getString("codigo_funcionario"), rs.getString("oficina"), rs.getString("cargo"),
                rs.getLong("bienes"), rs.getBoolean("es_custodia"), rs.getBoolean("pendiente_dbf") });

        Map<Long, Boolean> pendiente = new HashMap<>();
        for (Object[] o : crudas) pendiente.put((Long) o[0], (Boolean) o[10]);
        Map<Long, VsiafApoyoService.EstadoVsiaf> estados = vsiafApoyoService.estados(VsiafApoyoService.TABLA_RESP, pendiente);

        List<Fila> out = new ArrayList<>();
        for (Object[] o : crudas) {
            VsiafApoyoService.EstadoVsiaf e = estados.get((Long) o[0]);
            out.add(new Fila((Long) o[0], (Long) o[1], (Long) o[2], (String) o[3], (Short) o[4], (String) o[5],
                    (String) o[6], (String) o[7], (Long) o[8], (Boolean) o[9], (Boolean) o[10],
                    e == null ? VsiafApoyoService.EST_VSIAF : e.codigo()));
        }
        return out;
    }

    /** Personas no fusionadas cuyo C.I. tiene ese número (sin extensión ni complemento). */
    private List<Long> personasConNumeroCi(String numero) {
        return jdbc.queryForList("""
                SELECT id_persona FROM persona
                 WHERE _estado NOT IN ('FUSIONADA', 'ELIMINADO')
                   AND substring(regexp_replace(split_part(coalesce(ci, ''), '-', 1), '[.\\s]', '', 'g') from '^[0-9]+') = :n""",
                Map.of("n", numero), Long.class);
    }

    private List<Long> idsUnificadasEn(Long id) {
        return jdbc.queryForList("SELECT id_persona FROM persona WHERE fusionada_en = :id", Map.of("id", id), Long.class);
    }

    // ── Conversión ────────────────────────────────────────────────────────

    private static Map<String, Object> aMapa(DatosPersona p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("nombre", p.nombre());
        m.put("paterno", p.paterno());
        m.put("materno", p.materno());
        m.put("nombreCompleto", p.nombreCompleto());
        m.put("ci", p.ci());
        m.put("estado", p.estado());
        m.put("tieneUsuario", p.tieneUsuario());
        return m;
    }

    private static Map<String, Object> aMapa(Fila f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idResponsable", f.idResponsable());
        m.put("idPersona", f.idPersona());
        m.put("unidad", f.unidad());
        m.put("codOfic", f.codOfic());
        m.put("codResp", f.codResp());
        m.put("oficina", f.oficina());
        m.put("cargo", f.cargo());
        m.put("bienes", f.bienes());
        m.put("esCustodia", f.esCustodia());
        m.put("estadoVsiaf", f.estadoVsiaf());
        m.put("referencia", f.referencia());
        return m;
    }

    public static Map<String, Object> aMapa(Resultado r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("errores", r.errores());
        m.put("avisos", r.avisos());
        m.put("firma", r.firma());
        m.put("ciEnConflicto", r.ciEnConflicto());
        m.put("cambiaDatosPersona", r.cambiaDatosPersona());
        m.put("nomresp", PlanPersona.nomresp(r.nuevaPersona()));
        m.put("ci", r.nuevaPersona().ci());
        m.put("cargo", r.cargo());
        m.put("unifica", r.idsSecundarias().size());
        m.put("fusiona", r.idsFusionar().size());
        m.put("filasQueCambian", r.filasQueCambian());
        m.put("filasAlVsiaf", r.filasAlVsiaf());
        List<Map<String, Object>> filas = new ArrayList<>();
        for (CambioFila c : r.filas()) {
            Map<String, Object> f = aMapa(c.fila());
            f.put("idPersonaAntes", c.idPersonaAntes());
            f.put("nombreAntes", c.nombreAntes());
            f.put("ciAntes", c.ciAntes());
            f.put("cargoAntes", c.cargoAntes());
            f.put("nombreDespues", c.nombreDespues());
            f.put("ciDespues", c.ciDespues());
            f.put("cargoDespues", c.cargoDespues());
            f.put("cambiaPersona", c.cambiaPersona());
            f.put("cambiaDatos", c.cambiaDatos());
            f.put("cambiaCargo", c.cambiaCargo());
            f.put("hayCambio", c.hayCambio());
            f.put("enviaVsiaf", c.enviaVsiaf());
            f.put("nota", c.notaVsiaf());
            filas.add(f);
        }
        m.put("filas", filas);
        return m;
    }

    private static Map<String, Object> filaJson(Long idPersona, String nombre, String ci, String cargo, Long idCargo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idPersona", idPersona);
        m.put("nomresp", nombre);
        m.put("ci", ci);
        m.put("cargo", cargo);
        m.put("idCargo", idCargo);
        return m;
    }

    private static String unir(String... partes) {
        StringBuilder sb = new StringBuilder();
        for (String p : partes) if (p != null && !p.isBlank()) sb.append(sb.length() > 0 ? " " : "").append(p.trim());
        return sb.toString();
    }

    private static String recortar(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    static Long aLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        String t = o.toString().trim();
        return t.isEmpty() ? null : Long.valueOf(t);
    }

    /** Lista de ids desde el JSON de la pantalla. */
    public static List<Long> idsDe(Object o) {
        return aLongs(o);
    }

    static List<Long> aLongs(Object o) {
        List<Long> out = new ArrayList<>();
        if (o instanceof Collection<?> c) {
            for (Object x : c) {
                Long l = aLong(x);
                if (l != null) out.add(l);
            }
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
