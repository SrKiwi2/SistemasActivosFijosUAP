package com.usic.SistemasActivosFijosUAP.model.service.supervision;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;
import com.usic.SistemasActivosFijosUAP.model.dao.IParametroSistemaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ISolicitudAutorizacionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ParametroSistema;
import com.usic.SistemasActivosFijosUAP.model.entity.SolicitudAutorizacion;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import lombok.extern.slf4j.Slf4j;

/**
 * Solicitudes de autorización para operaciones de alto impacto.
 * <p>
 * Quién resuelve: el ADMINISTRADOR, o el SUPER USUARIO que el administrador designe como
 * revisor (parámetro {@link #CLAVE_REVISOR}). Solo el revisor designado recibe el aviso
 * de cada solicitud nueva. Todo el circuito es en vivo por SSE:
 * <ul>
 *   <li>{@code autorizacion} con {@code evento=NUEVA} → al revisor.</li>
 *   <li>{@code autorizacion} con {@code evento=RESUELTA} → al solicitante (y al revisor,
 *       para refrescar su lista si la resolvió otro).</li>
 * </ul>
 */
@Slf4j
@Service
public class AutorizacionService {

    public static final String CLAVE_REVISOR = "autorizacion.revisor.id_usuario";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ISolicitudAutorizacionDao dao;
    private final IParametroSistemaDao parametroDao;
    private final IUsuarioDao usuarioDao;
    private final SseEmitterRegistry sseRegistry;
    private final ActividadService actividadService;
    private final Map<String, EjecutorAutorizacion> ejecutores = new HashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public AutorizacionService(ISolicitudAutorizacionDao dao, IParametroSistemaDao parametroDao,
                               IUsuarioDao usuarioDao, SseEmitterRegistry sseRegistry,
                               ActividadService actividadService, List<EjecutorAutorizacion> ejecutores) {
        this.dao = dao;
        this.parametroDao = parametroDao;
        this.usuarioDao = usuarioDao;
        this.sseRegistry = sseRegistry;
        this.actividadService = actividadService;
        for (EjecutorAutorizacion e : ejecutores) {
            for (String t : e.tipos()) this.ejecutores.put(t, e);
        }
    }

    // ── Revisor designado ───────────────────────────────────────────────────

    public Long idRevisor() {
        return parametroDao.findById(CLAVE_REVISOR)
                .map(ParametroSistema::getValor)
                .filter(v -> v != null && !v.isBlank())
                .map(Long::valueOf)
                .orElse(null);
    }

    public Usuario revisor() {
        Long id = idRevisor();
        return id != null ? usuarioDao.findById(id).orElse(null) : null;
    }

    /** Solo el ADMINISTRADOR designa al revisor, y tiene que ser un SUPER USUARIO. */
    public void designarRevisor(Long idUsuario, Usuario admin) {
        if (!RolesSciaf.esAdministrador(admin)) {
            throw new IllegalStateException("Solo el ADMINISTRADOR puede designar al revisor.");
        }
        if (idUsuario != null) {
            Usuario u = usuarioDao.findById(idUsuario)
                    .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
            if (!RolesSciaf.SUPER_USUARIO.equals(RolesSciaf.rolDe(u))) {
                throw new IllegalArgumentException("El revisor debe tener rol SUPER USUARIO.");
            }
        }
        ParametroSistema p = parametroDao.findById(CLAVE_REVISOR).orElseGet(ParametroSistema::new);
        p.setClave(CLAVE_REVISOR);
        p.setValor(idUsuario != null ? idUsuario.toString() : null);
        p.setFechaModificacion(LocalDateTime.now());
        p.setUsuario(admin.getUsuario());
        parametroDao.save(p);
    }

    /** ADMINISTRADOR o el SUPER USUARIO designado. */
    public boolean puedeRevisar(Usuario u) {
        if (u == null) return false;
        if (RolesSciaf.esAdministrador(u)) return true;
        Long id = idRevisor();
        return id != null && id.equals(u.getIdUsuario());
    }

    // ── Solicitar ───────────────────────────────────────────────────────────

    /**
     * Crea la solicitud (no aplica nada) y avisa al revisor.
     *
     * @throws IllegalStateException si ya hay una pendiente sobre el mismo registro
     */
    public SolicitudAutorizacion solicitar(String tipo, String modulo, Long idRegistro, String referencia,
                                           String resumen, Map<String, Object> datos, String motivo,
                                           Usuario solicitante) {
        if (!ejecutores.containsKey(tipo)) throw new IllegalArgumentException("Tipo de solicitud desconocido: " + tipo);
        if (motivo == null || motivo.isBlank()) {
            throw new IllegalArgumentException("Indique el motivo de la solicitud.");
        }
        if (dao.existsByModuloAndIdRegistroAndEstado(modulo, idRegistro, SolicitudAutorizacion.PENDIENTE)) {
            throw new IllegalStateException("Ya hay una solicitud pendiente sobre este registro. "
                    + "Espere a que el revisor la resuelva.");
        }

        SolicitudAutorizacion s = new SolicitudAutorizacion();
        s.setTipo(tipo);
        s.setModulo(modulo);
        s.setIdRegistro(idRegistro);
        s.setReferencia(referencia);
        s.setResumen(resumen);
        s.setMotivo(motivo.trim());
        s.setIdSolicitante(solicitante.getIdUsuario());
        s.setSolicitante(solicitante.getUsuario());
        try {
            s.setDatosJson(mapper.writeValueAsString(datos));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron guardar los datos de la solicitud: " + e.getMessage());
        }
        dao.save(s);

        actividadService.registrar(solicitante, ActividadService.MOD_AUTORIZACION, ActividadService.ACC_SOLICITUD,
                referencia, "Solicitó autorización para " + describirTipo(tipo) + " " + referencia
                        + (resumen != null ? " (" + resumen + ")" : "") + ". Motivo: " + s.getMotivo(),
                s.getIdSolicitud());

        avisarRevisor(s);
        return s;
    }

    // ── Resolver ────────────────────────────────────────────────────────────

    /**
     * Aprueba y ejecuta. Si al ejecutarla ya no se puede (p. ej. la oficina tiene ahora
     * responsables), queda FALLIDA con el motivo y se le avisa igual al solicitante.
     */
    public SolicitudAutorizacion aprobar(Long idSolicitud, String comentario, Usuario revisor) {
        SolicitudAutorizacion s = pendienteParaRevisar(idSolicitud, revisor);

        // Se marca primero (con @Version): si otro revisor la resolvió en paralelo, acá falla.
        s.setEstado(SolicitudAutorizacion.APROBADA);
        s.setIdRevisor(revisor.getIdUsuario());
        s.setRevisor(revisor.getUsuario());
        s.setFechaRevision(LocalDateTime.now());
        try {
            s = dao.saveAndFlush(s);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException("La solicitud ya fue resuelta por otro revisor.");
        }

        Usuario solicitante = usuarioDao.findById(s.getIdSolicitante()).orElse(revisor);
        String resultado;
        try {
            Map<String, Object> datos = mapper.readValue(s.getDatosJson(), new TypeReference<Map<String, Object>>() {});
            resultado = ejecutores.get(s.getTipo()).ejecutar(s.getTipo(), datos, solicitante);
        } catch (Exception e) {
            log.warn("[AUTORIZACION] Solicitud {} aprobada pero no se pudo aplicar: {}", s.getIdSolicitud(), e.getMessage());
            s.setEstado(SolicitudAutorizacion.FALLIDA);
            resultado = "Se aprobó, pero no se pudo aplicar: " + e.getMessage();
        }
        s.setRespuesta(unir(comentario, resultado));
        dao.save(s);

        actividadService.registrar(revisor, ActividadService.MOD_AUTORIZACION, ActividadService.ACC_APROBACION,
                s.getReferencia(), "Aprobó la solicitud de " + s.getSolicitante() + " para "
                        + describirTipo(s.getTipo()) + " " + s.getReferencia() + ". " + resultado,
                s.getIdSolicitud());
        avisarResolucion(s);
        return s;
    }

    public SolicitudAutorizacion rechazar(Long idSolicitud, String comentario, Usuario revisor) {
        if (comentario == null || comentario.isBlank()) {
            throw new IllegalArgumentException("Indique el motivo del rechazo.");
        }
        SolicitudAutorizacion s = pendienteParaRevisar(idSolicitud, revisor);
        s.setEstado(SolicitudAutorizacion.RECHAZADA);
        s.setIdRevisor(revisor.getIdUsuario());
        s.setRevisor(revisor.getUsuario());
        s.setFechaRevision(LocalDateTime.now());
        s.setRespuesta(comentario.trim());
        try {
            s = dao.saveAndFlush(s);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new IllegalStateException("La solicitud ya fue resuelta por otro revisor.");
        }
        actividadService.registrar(revisor, ActividadService.MOD_AUTORIZACION, ActividadService.ACC_RECHAZO,
                s.getReferencia(), "Rechazó la solicitud de " + s.getSolicitante() + " para "
                        + describirTipo(s.getTipo()) + " " + s.getReferencia() + ". Motivo: " + s.getRespuesta(),
                s.getIdSolicitud());
        avisarResolucion(s);
        return s;
    }

    /** El solicitante retira su propia solicitud mientras sigue pendiente. */
    public SolicitudAutorizacion anular(Long idSolicitud, Usuario solicitante) {
        SolicitudAutorizacion s = dao.findById(idSolicitud)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada."));
        if (!s.getIdSolicitante().equals(solicitante.getIdUsuario())) {
            throw new IllegalStateException("Solo quien la pidió puede anularla.");
        }
        if (!SolicitudAutorizacion.PENDIENTE.equals(s.getEstado())) {
            throw new IllegalStateException("La solicitud ya fue resuelta.");
        }
        s.setEstado(SolicitudAutorizacion.ANULADA);
        s.setFechaRevision(LocalDateTime.now());
        dao.save(s);
        avisarRevisor(s);   // para que desaparezca de su lista
        return s;
    }

    private SolicitudAutorizacion pendienteParaRevisar(Long idSolicitud, Usuario revisor) {
        if (!puedeRevisar(revisor)) {
            throw new IllegalStateException("No está designado como revisor de autorizaciones.");
        }
        SolicitudAutorizacion s = dao.findById(idSolicitud)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada."));
        if (!SolicitudAutorizacion.PENDIENTE.equals(s.getEstado())) {
            throw new IllegalStateException("La solicitud ya fue resuelta (" + s.getEstado() + ").");
        }
        return s;
    }

    // ── Consultas ───────────────────────────────────────────────────────────

    public List<SolicitudAutorizacion> pendientes() {
        return dao.findByEstadoOrderByFechaSolicitudAsc(SolicitudAutorizacion.PENDIENTE);
    }

    public List<SolicitudAutorizacion> historial(int max) {
        return dao.findByEstadoNotOrderByFechaRevisionDesc(SolicitudAutorizacion.PENDIENTE, PageRequest.of(0, max));
    }

    public long contarPendientes() {
        return dao.countByEstado(SolicitudAutorizacion.PENDIENTE);
    }

    /** Ids de un módulo con solicitud pendiente, para marcarlos en su tabla. */
    public Set<Long> idsConSolicitudPendiente(String modulo, java.util.Collection<Long> ids) {
        Set<Long> out = new HashSet<>();
        if (ids == null || ids.isEmpty()) return out;
        for (SolicitudAutorizacion s : dao.findByModuloAndEstadoAndIdRegistroIn(modulo, SolicitudAutorizacion.PENDIENTE, ids)) {
            out.add(s.getIdRegistro());
        }
        return out;
    }

    public Map<String, Object> aMapa(SolicitudAutorizacion s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("idSolicitud", s.getIdSolicitud());
        m.put("tipo", s.getTipo());
        m.put("tipoTexto", describirTipo(s.getTipo()));
        m.put("modulo", s.getModulo());
        m.put("referencia", s.getReferencia());
        m.put("resumen", s.getResumen());
        m.put("motivo", s.getMotivo());
        m.put("estado", s.getEstado());
        m.put("solicitante", s.getSolicitante());
        m.put("fechaSolicitud", s.getFechaSolicitud() != null ? s.getFechaSolicitud().format(FMT) : "");
        m.put("revisor", s.getRevisor());
        m.put("fechaRevision", s.getFechaRevision() != null ? s.getFechaRevision().format(FMT) : "");
        m.put("respuesta", s.getRespuesta());
        return m;
    }

    public static String describirTipo(String tipo) {
        return switch (tipo) {
            case OficinaGestionService.TIPO_MODIFICAR -> "cambiar predio/código de la oficina";
            case OficinaGestionService.TIPO_ELIMINAR -> "eliminar la oficina";
            case ResponsableGestionService.TIPO_MODIFICAR -> "cambiar oficina/código del responsable";
            case ResponsableGestionService.TIPO_ELIMINAR -> "eliminar al responsable";
            default -> tipo;
        };
    }

    // ── Avisos SSE ──────────────────────────────────────────────────────────

    private void avisarRevisor(SolicitudAutorizacion s) {
        try {
            Map<String, Object> payload = aMapa(s);
            payload.put("evento", SolicitudAutorizacion.PENDIENTE.equals(s.getEstado()) ? "NUEVA" : "RETIRADA");
            payload.put("pendientes", contarPendientes());
            Long id = idRevisor();
            if (id != null) {
                sseRegistry.enviarAUsuario(id, "autorizacion", payload);
            } else {
                // Sin revisor designado: que al menos lo vea el administrador.
                for (Long adm : sseRegistry.getUsuariosConectadosEnRol(RolesSciaf.ADMINISTRADOR)) {
                    sseRegistry.enviarAUsuario(adm, "autorizacion", payload);
                }
            }
        } catch (Exception e) {
            log.debug("[AUTORIZACION] No se pudo avisar al revisor: {}", e.getMessage());
        }
    }

    private void avisarResolucion(SolicitudAutorizacion s) {
        try {
            Map<String, Object> payload = aMapa(s);
            payload.put("evento", "RESUELTA");
            payload.put("pendientes", contarPendientes());
            sseRegistry.enviarAUsuario(s.getIdSolicitante(), "autorizacion", payload);
            Long id = idRevisor();
            if (id != null && !id.equals(s.getIdRevisor())) sseRegistry.enviarAUsuario(id, "autorizacion", payload);
        } catch (Exception e) {
            log.debug("[AUTORIZACION] No se pudo avisar la resolución: {}", e.getMessage());
        }
    }

    private static String unir(String a, String b) {
        boolean hayA = a != null && !a.isBlank();
        boolean hayB = b != null && !b.isBlank();
        if (hayA && hayB) return a.trim() + " — " + b;
        return hayA ? a.trim() : (hayB ? b : null);
    }
}
