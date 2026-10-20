package com.usic.SistemasActivosFijosUAP.model.service.duplicados;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDuplicadoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IConfiguracionGestionDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHallazgoInventarioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.INotificacionDuplicadoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.duplicados.DuplicadosDTOs;
import com.usic.SistemasActivosFijosUAP.model.entity.ActaFaltante;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.ActivoDuplicado;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.HallazgoInventario;
import com.usic.SistemasActivosFijosUAP.model.entity.NotificacionDuplicado;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.DuplicadosRepo;
import com.usic.SistemasActivosFijosUAP.model.service.TransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.service.control.ControlActivosService;
import com.usic.SistemasActivosFijosUAP.model.service.control.DatosTecnicos;
import com.usic.SistemasActivosFijosUAP.model.service.control.EnvioCustodiaService;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglasCustodia;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.OficinaGestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Activos duplicados: el mismo bien físico registrado dos veces en el VSIAF con dos códigos
 * (error del revalúo). Lo identifica una persona frente al bien (en el barrido el registro
 * que sobra sale como faltante); el sistema solo registra, mueve y documenta.
 * <ul>
 *   <li><b>Registrar</b>: el código que sobra (B) va a la oficina de duplicados de su predio,
 *       a nombre de la misma persona; el que se queda (A) no se toca. Se emite la
 *       notificación NOT:SCIAF:AD. Si B figuraba como faltante, ese faltante se cierra con
 *       resolución {@code DUPLICADO} y la notificación lo dice.</li>
 *   <li><b>Retirar</b>: B sale de la oficina de duplicados hacia un responsable del predio,
 *       con justificación (transferencia interna que viaja al VSIAF).</li>
 *   <li><b>Anular</b>: se registró por error y B todavía no se movió.</li>
 * </ul>
 * No se corrige ni se da de baja nada: eso lo hará el nuevo revalúo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicadosService {

    /** Resolución con la que se cierra un faltante que resultó ser un duplicado. */
    public static final String RESOLUCION_DUPLICADO = "DUPLICADO";

    private static final long TURNO_NUMERACION = 0x5C1AD0001L;
    private static final String CIUDAD_POR_DEFECTO = "Cobija";
    private static final int MIN_MOTIVO = 10;
    private static final int MAX_TEXTO = 2000;

    private final IActivoDao activoDao;
    private final IActivoDuplicadoDao duplicadoDao;
    private final INotificacionDuplicadoDao notificacionDao;
    private final IHallazgoInventarioDao hallazgoDao;
    private final IConfiguracionGestionDao configuracionDao;
    private final IUsuarioDao usuarioDao;
    private final com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao colaDao;
    private final IResponsableService responsableService;
    private final DuplicadosRepo repo;
    private final CustodiaDuplicadosService custodiaService;
    private final EnvioDuplicadosService envioService;
    private final ControlActivosService controlActivosService;
    private final TransferenciaService transferenciaService;
    private final ActualDbfWriterService actualDbfWriterService;
    private final ActividadService actividadService;
    private final PlatformTransactionManager txManager;

    private final ObjectMapper json = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final SecureRandom azar = new SecureRandom();

    public record Registrado(Long idDuplicado, String numero, String mensaje) {}

    // ── Consultas ───────────────────────────────────────────────────────────

    public DuplicadosDTOs.Ficha fichaPorCodigo(String codigo) {
        DuplicadosDTOs.Ficha f = repo.fichaPorCodigo(codigo);
        if (f == null) throw new ReglaNegocioException("No hay ningún activo con el código " + (codigo == null ? "" : codigo.trim()) + ".");
        return f;
    }

    /** Activos parecidos al indicado (solo sugerencias). */
    public List<DuplicadosDTOs.Gemelo> gemelos(Long idActivo) {
        DuplicadosDTOs.Ficha f = repo.fichaPorId(idActivo);
        if (f == null) throw new ReglaNegocioException("El activo no existe.");
        return repo.gemelos(f, 15);
    }

    public Map<String, Object> tabla(int draw, int start, int length, String q, String estado) {
        int cuantos = Math.max(1, Math.min(length <= 0 ? 25 : length, 200));
        Map<String, Object> m = new LinkedHashMap<>();
        long total = repo.contar(null, null);
        m.put("draw", draw);
        m.put("recordsTotal", total);
        m.put("recordsFiltered", (q == null || q.isBlank()) && (estado == null || estado.isBlank()) ? total : repo.contar(q, estado));
        m.put("data", repo.pagina(q, estado, Math.max(0, start), cuantos));
        m.put("totales", repo.totales());
        return m;
    }

    public Map<String, Long> totales() {
        return repo.totales();
    }

    /** Responsables a los que puede volver el bien al retirarlo (mismo predio). */
    public List<DuplicadosDTOs.Destino> destinos(Long idCaso, String texto) {
        return enTransaccion(() -> {
            ActivoDuplicado d = duplicadoDao.findById(idCaso).orElseThrow(() -> new ReglaNegocioException("El caso no existe."));
            Activo a = d.getActivo();
            if (a.getOficina() == null || a.getOficina().getPredio() == null) return List.of();
            Long original = d.getResponsableOrigen() != null ? d.getResponsableOrigen().getIdResponsable() : null;
            return repo.destinos(a.getOficina().getPredio().getIdPredio(), original, texto, 40);
        });
    }

    // ── Revisión antes de registrar ─────────────────────────────────────────

    /** Lo que pasaría al registrar, sin tocar nada. */
    public DuplicadosDTOs.Situacion situacion(Long idSobrante, Long idPrincipal) {
        return enTransaccion(() -> {
            Revision r = revisar(idSobrante, idPrincipal);
            CustodiaDuplicadosService.Prevision p = r.sobrante() != null && r.predio() != null
                    ? custodiaService.prever(r.predio(), r.persona()) : null;
            return new DuplicadosDTOs.Situacion(
                    repo.fichaPorId(idSobrante), idPrincipal != null ? repo.fichaPorId(idPrincipal) : null,
                    r.bloqueos(), r.avisos(), r.faltanteNumero(),
                    p != null ? p.oficina() : null, p != null && p.creaOficina(), p != null && p.creaResponsable(),
                    r.bloqueos().isEmpty());
        });
    }

    /**
     * @param avisoFuerte un aviso que exige confirmación expresa para registrar (el que se
     *                    queda figura como faltante: quizá se eligió al revés)
     */
    private record Revision(Activo sobrante, Activo principal, Predio predio, Persona persona,
                            HallazgoInventario faltante, String faltanteNumero,
                            List<String> bloqueos, List<String> avisos, boolean avisoFuerte) {}

    /** Valida el par (solo lee). Corre dentro de una transacción. */
    private Revision revisar(Long idSobrante, Long idPrincipal) {
        List<String> bloqueos = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        if (idSobrante == null) throw new ReglaNegocioException("Indique el activo que sobra.");
        Activo b = activoDao.findById(idSobrante).orElseThrow(() -> new ReglaNegocioException("El activo que sobra no existe."));
        Activo a = idPrincipal != null ? activoDao.findById(idPrincipal).orElse(null) : null;
        if (idPrincipal == null || a == null) bloqueos.add("Indique el activo que se queda (el que tiene el bien).");
        if (a != null && Objects.equals(a.getIdActivo(), b.getIdActivo())) {
            bloqueos.add("Los dos códigos son el mismo activo.");
        }

        // ── El que sobra ──
        if (!Activo.ESTADO_ACTIVO.equals(b.getEstado())) bloqueos.add(b.getCodigo() + " no está vigente (" + b.getEstado() + ").");
        if (Boolean.TRUE.equals(b.getBloqueado())) bloqueos.add(b.getCodigo() + " está bloqueado: desbloquéelo primero.");
        boolean yaRegistrado = !duplicadoDao.pendientesDelActivo(b.getIdActivo()).isEmpty();
        if (yaRegistrado) {
            bloqueos.add(b.getCodigo() + " ya está registrado como duplicado.");
        } else if (b.enDuplicados()) {
            // Llegó a la oficina de duplicados sin registro (oficina adoptada, o lo movieron en el
            // VSIAF): se regulariza. No se mueve; queda el caso y su notificación para poder seguirlo.
            avisos.add(b.getCodigo() + " ya está en la oficina de duplicados sin registro: se regulariza "
                    + "(no se mueve; queda el registro y la notificación).");
        }
        if (!duplicadoDao.pendientesComoPrincipal(b.getIdActivo()).isEmpty()) {
            bloqueos.add(b.getCodigo() + " es el activo que se queda en otro caso de duplicado: no puede ser también el que sobra.");
        }
        Responsable rb = b.getResponsable();
        Oficina ob = b.getOficina();
        Predio predio = ob != null ? ob.getPredio() : null;
        Persona persona = rb != null ? rb.getPersona() : null;
        if (rb == null || persona == null) bloqueos.add(b.getCodigo() + " no tiene responsable con persona registrada: no se sabe a nombre de quién queda.");
        if (predio == null) bloqueos.add(b.getCodigo() + " no tiene oficina o predio.");
        else if (predio.getEntidad() == null || predio.getUnidad() == null || predio.getUnidad().isBlank()) {
            bloqueos.add("El predio " + predio.getDescrip() + " no tiene entidad o unidad: no se puede registrar en el VSIAF.");
        }
        if (b.esSoloSciaf()) avisos.add(b.getCodigo() + " existe solo en el SCIAF: se mueve en el SCIAF y no viaja al VSIAF.");

        // ── ¿Figuraba como faltante? ──
        HallazgoInventario faltante = null;
        String faltanteNumero = null;
        List<HallazgoInventario> pendientes = hallazgoDao.pendientesDelActivo(b.getIdActivo());
        if (!pendientes.isEmpty()) {
            faltante = pendientes.get(0);
            faltanteNumero = faltante.getActa() != null ? ActaFaltante.numeroImpreso(faltante.getActa().getNumero()) : null;
            String envio = faltante.getEstadoEnvio();
            if (EnvioCustodiaService.ESPERANDO_ALTA.equals(envio) || EnvioCustodiaService.ENVIADO.equals(envio)) {
                bloqueos.add(b.getCodigo() + " se está trasladando a la oficina de faltantes"
                        + (faltanteNumero != null ? " (" + faltanteNumero + ")" : "") + ". Espere a que el VSIAF lo confirme.");
            } else if (EnvioCustodiaService.ERROR.equals(envio) && faltante.getFechaEnvioCustodia() != null) {
                bloqueos.add("El VSIAF rechazó el traslado de " + b.getCodigo() + " a la oficina de faltantes. "
                        + "Reintente ese traslado en Faltantes antes de registrarlo como duplicado.");
            } else {
                avisos.add(b.getCodigo() + " figura como FALTANTE" + (faltanteNumero != null ? " (" + faltanteNumero + ")" : "")
                        + ": ese faltante se cerrará como «duplicado» y la notificación lo aclarará.");
            }
        } else if (b.enCustodia()) {
            avisos.add(b.getCodigo() + " está en la oficina de faltantes sin un faltante abierto: pasará de ahí a la de duplicados.");
        }

        // ── El que se queda ──
        boolean avisoFuerte = false;
        if (a != null && !Objects.equals(a.getIdActivo(), b.getIdActivo())) {
            if (!Activo.ESTADO_ACTIVO.equals(a.getEstado())) bloqueos.add(a.getCodigo() + " (el que se queda) no está vigente.");
            if (a.enDuplicados() || !duplicadoDao.pendientesDelActivo(a.getIdActivo()).isEmpty()) {
                bloqueos.add(a.getCodigo() + " (el que se queda) ya está registrado como duplicado de otro.");
            }
            if (!hallazgoDao.pendientesDelActivo(a.getIdActivo()).isEmpty() || a.enCustodia()) {
                avisoFuerte = true;
                avisos.add("ATENCIÓN: " + a.getCodigo() + ", el que se queda, figura como FALTANTE. "
                        + "Si el bien que se encontró tiene la etiqueta de " + b.getCodigo() + ", invierta los códigos.");
            }
            Predio pa = a.getOficina() != null ? a.getOficina().getPredio() : null;
            if (pa != null && predio != null && !Objects.equals(pa.getIdPredio(), predio.getIdPredio())) {
                avisos.add("Están en predios distintos (" + predio.getUnidad() + " y " + pa.getUnidad() + "). "
                        + b.getCodigo() + " va a la oficina de duplicados de su predio, " + predio.getUnidad() + ".");
            }
            if (a.getGrupoContable() != null && b.getGrupoContable() != null
                    && !Objects.equals(a.getGrupoContable().getIdGrupoContable(), b.getGrupoContable().getIdGrupoContable())) {
                avisos.add("Tienen grupos contables distintos: revise que sean el mismo bien.");
            }
            Persona pa2 = a.getResponsable() != null ? a.getResponsable().getPersona() : null;
            if (pa2 != null && persona != null && !Objects.equals(pa2.getIdPersona(), persona.getIdPersona())) {
                avisos.add("Están a nombre de personas distintas: " + b.getCodigo() + " queda a nombre de "
                        + persona.getNombreCompleto() + " en la oficina de duplicados.");
            }
        }
        return new Revision(b, a, predio, persona, faltante, faltanteNumero, bloqueos, avisos, avisoFuerte);
    }

    // ── Registro ────────────────────────────────────────────────────────────

    public Registrado registrar(DuplicadosDTOs.Registro req, Usuario autor) {
        String motivo = textoValido(req != null ? req.motivo() : null, "el motivo");
        Creado creado = enTransaccion(() -> crear(req, motivo, autor));
        String numero = NotificacionDuplicado.numeroImpreso(creado.numero());
        actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_REGISTRO, creado.codigo(),
                "Registró " + creado.codigo() + " como duplicado de " + creado.codigoPrincipal() + " (" + numero + ")"
                        + (creado.faltanteCerrado() ? "; cerró su faltante" : ""), creado.idDuplicado());
        if (creado.regularizado()) {
            envioService.emitir(1);
            return new Registrado(creado.idDuplicado(), numero, "Notificación " + numero + " emitida. " + creado.codigo()
                    + " ya estaba en la oficina de duplicados: quedó regularizado sin moverlo.");
        }
        envioService.iniciar(List.of(creado.idDuplicado()), autor);
        envioService.emitir(1);
        return new Registrado(creado.idDuplicado(), numero, "Notificación " + numero + " emitida. " + creado.codigo()
                + " pasará a la oficina de duplicados cuando el VSIAF confirme el alta de la oficina y de la persona.");
    }

    private record Creado(Long idDuplicado, String numero, String codigo, String codigoPrincipal, boolean faltanteCerrado,
                          boolean regularizado) {}

    private Creado crear(DuplicadosDTOs.Registro req, String motivo, Usuario autor) {
        // El turno se toma antes de revisar: dos registros a la vez (p. ej. el mismo par invertido)
        // se validan uno después del otro, y el segundo ya ve al primero.
        notificacionDao.turnoNumeracion(TURNO_NUMERACION);
        Revision r = revisar(req.idSobrante(), req.idPrincipal());
        if (!r.bloqueos().isEmpty()) {
            throw new ReglaNegocioException("No se registró nada: " + String.join(" ", r.bloqueos()));
        }
        if (r.avisoFuerte() && !Boolean.TRUE.equals(req.confirmaAvisos())) {
            throw new ReglaNegocioException("Revise el aviso: el activo que se queda figura como faltante. "
                    + "Si está seguro, confirme y vuelva a registrar.");
        }
        Activo b = r.sobrante();
        Activo a = r.principal();
        LocalDateTime ahora = LocalDateTime.now();
        String usuario = autor != null ? autor.getUsuario() : "SISTEMA";

        // Antes de tomar el turno y de insertar: sin firmante no se emite.
        ConfiguracionGestion conf = configuracionDao.findFirstByGestionAndEstadoOrderByIdConfigDesc(ahora.getYear(), "ACTIVO")
                .or(() -> configuracionDao.findFirstByGestionOrderByIdConfigDesc(ahora.getYear())).orElse(null);
        String firmante = conf != null ? limpio(conf.getResponsableActivosNombre()) : null;
        if (firmante == null) {
            throw new ReglaNegocioException("Falta el nombre del Responsable de Activos Fijos en la configuración de la gestión "
                    + ahora.getYear() + ". Cárguelo en Configuración antes de emitir la notificación.");
        }
        String ciudad = conf.getCiudad() != null && !conf.getCiudad().isBlank() ? conf.getCiudad().trim() : CIUDAD_POR_DEFECTO;

        // El turno (tomado arriba) sigue en esta transacción hasta confirmar: el correlativo no se repite.
        String numero = String.format("%s%03d/%d", NotificacionDuplicado.PREFIJO,
                notificacionDao.ultimoCorrelativo(String.valueOf(ahora.getYear())) + 1, ahora.getYear());

        Responsable rb = b.getResponsable();
        Persona persona = r.persona();
        CustodiaDuplicadosService.Prevision prev = custodiaService.prever(r.predio(), persona);
        DuplicadosDTOs.Contenido c = new DuplicadosDTOs.Contenido(numero, ahora.withNano(0).toString(), ciudad, firmante,
                persona.getNombreCompleto(), limpio(persona.getCi()),
                rb.getCargo() != null ? rb.getCargo().getNombre() : null,
                b.getOficina() != null ? b.getOficina().getNombre() : null,
                nombreDe(autor), usuario, motivo,
                bienDoc(b), bienDoc(a), r.faltanteNumero(), prev.oficina());

        NotificacionDuplicado n = new NotificacionDuplicado();
        n.setNumero(numero);
        n.setToken(nuevoToken());
        n.setPersona(persona);
        n.setPersonaNombre(recortar(persona.getNombreCompleto(), 160));
        n.setFechaEmision(ahora);
        n.setUsuarioEmision(usuario);
        n.setContenido(aJson(c));
        n.setHashContenido(sha256(n.getContenido()));
        n.setEstadoNotificacion(NotificacionDuplicado.VIGENTE);
        n.setEstado("ACTIVO");
        if (autor != null) n.setRegistroIdUsuario(autor.getIdUsuario());
        notificacionDao.save(n);

        ActivoDuplicado d = new ActivoDuplicado();
        d.setActivo(b);
        d.setActivoPrincipal(a);
        d.setMotivo(motivo);
        boolean regulariza = b.enDuplicados();
        if (regulariza) {
            d.setEstadoDuplicado(ActivoDuplicado.EN_DUPLICADOS);
            d.setEstadoEnvio(EnvioDuplicadosService.CONFIRMADO);
            d.setResponsableCustodia(rb);
            d.setFechaEnvio(ahora);
            d.setRegularizado(true);
            d.setMensajeEnvio("Ya estaba en la oficina de duplicados: se regularizó sin moverlo.");
        } else {
            d.setEstadoDuplicado(ActivoDuplicado.EN_PROCESO);
            d.setEstadoEnvio(EnvioDuplicadosService.ESPERANDO_ALTA);
        }
        d.setResponsableOrigen(rb);
        d.setOficinaOrigen(b.getOficina());
        d.setPersona(persona);
        d.setUsuarioRegistro(usuario);
        d.setNotificacion(n);
        d.setEstado("ACTIVO");
        if (autor != null) d.setRegistroIdUsuario(autor.getIdUsuario());

        boolean faltanteCerrado = false;
        HallazgoInventario h = r.faltante();
        if (h != null) {
            List<HallazgoInventario> bloqueado = hallazgoDao.bloquear(List.of(h.getIdHallazgo()));
            h = bloqueado.isEmpty() ? null : bloqueado.get(0);
        }
        if (h != null && (EnvioCustodiaService.ESPERANDO_ALTA.equals(h.getEstadoEnvio())
                || EnvioCustodiaService.ENVIADO.equals(h.getEstadoEnvio())
                || (EnvioCustodiaService.ERROR.equals(h.getEstadoEnvio()) && h.getFechaEnvioCustodia() != null))) {
            // Mientras se revisaba, alguien emitió su notificación de faltantes y el traslado empezó.
            throw new ReglaNegocioException("No se registró nada: " + b.getCodigo()
                    + " se está trasladando a la oficina de faltantes. Espere a que termine y vuelva a intentar.");
        }
        if (h != null && ControlActivosService.PENDIENTES.contains(h.getEstadoHallazgo())) {
            d.setFaltanteEstadoEnvio(h.getEstadoEnvio());
            if (EnvioCustodiaService.ERROR.equals(h.getEstadoEnvio())) {
                // El traslado a faltantes falló antes de mover el bien: ya no corre.
                h.setEstadoEnvio(null);
                h.setMensajeEnvio("Traslado cerrado: el bien se registró como duplicado.");
            }
            h.setEstadoHallazgo(ControlActivosService.RESUELTO);
            h.setTipoResolucion(RESOLUCION_DUPLICADO);
            h.setAccionCorrectiva(recortar("Registrado como duplicado de " + a.getCodigo() + " ("
                    + NotificacionDuplicado.numeroImpreso(numero) + "): es el mismo bien que ese activo. Motivo: " + motivo, MAX_TEXTO));
            h.setFechaResolucion(ahora);
            h.setUsuarioRevisor(usuario);
            if (h.getInventario() != null) controlActivosService.recalcularFaltantesDe(h.getInventario());
            d.setHallazgoFaltante(h);
            faltanteCerrado = true;
        }
        duplicadoDao.saveAndFlush(d);
        log.info("[DUPLICADOS] {} registrado como duplicado de {} ({}) por {}{}", b.getCodigo(), a.getCodigo(), numero, usuario,
                regulariza ? " (regularizado: ya estaba en la oficina de duplicados)" : "");
        return new Creado(d.getIdDuplicado(), numero, b.getCodigo(), a.getCodigo(), faltanteCerrado, regulariza);
    }

    /** La notificación como quedaría, sin guardar nada (para la vista previa en PDF). */
    public DuplicadosDTOs.Notificacion vistaPrevia(DuplicadosDTOs.Registro req, Usuario autor) {
        String motivo = textoValido(req != null ? req.motivo() : null, "el motivo");
        return enTransaccion(() -> {
            Revision r = revisar(req.idSobrante(), req.idPrincipal());
            if (!r.bloqueos().isEmpty()) throw new ReglaNegocioException(String.join(" ", r.bloqueos()));
            LocalDateTime ahora = LocalDateTime.now();
            ConfiguracionGestion conf = configuracionDao.findFirstByGestionAndEstadoOrderByIdConfigDesc(ahora.getYear(), "ACTIVO")
                    .or(() -> configuracionDao.findFirstByGestionOrderByIdConfigDesc(ahora.getYear())).orElse(null);
            Responsable rb = r.sobrante().getResponsable();
            CustodiaDuplicadosService.Prevision prev = custodiaService.prever(r.predio(), r.persona());
            DuplicadosDTOs.Contenido c = new DuplicadosDTOs.Contenido(null, ahora.withNano(0).toString(),
                    conf != null && conf.getCiudad() != null && !conf.getCiudad().isBlank() ? conf.getCiudad().trim() : CIUDAD_POR_DEFECTO,
                    conf != null ? limpio(conf.getResponsableActivosNombre()) : null,
                    r.persona().getNombreCompleto(), limpio(r.persona().getCi()),
                    rb.getCargo() != null ? rb.getCargo().getNombre() : null,
                    r.sobrante().getOficina() != null ? r.sobrante().getOficina().getNombre() : null,
                    nombreDe(autor), autor != null ? autor.getUsuario() : null, motivo,
                    bienDoc(r.sobrante()), bienDoc(r.principal()), r.faltanteNumero(), prev.oficina());
            return new DuplicadosDTOs.Notificacion(null, null, "VISTA PREVIA", null, null, null, null, null,
                    null, null, null, true, c);
        });
    }

    // ── Retiro y anulación ──────────────────────────────────────────────────

    /**
     * Saca el bien de la oficina de duplicados hacia un responsable del mismo predio, con
     * justificación: transferencia interna con historial y UPDATE de ACTUAL. Si la orden no
     * se puede dejar (montaje caído), no se mueve nada. Es el único camino de salida.
     */
    public String retirar(Long idCaso, DuplicadosDTOs.Retiro req, Usuario autor) {
        String justificacion = textoValido(req != null ? req.justificacion() : null, "la justificación");
        String usuario = autor != null ? autor.getUsuario() : "SISTEMA";
        String[] codigo = new String[1];
        String mensaje = enTransaccion(() -> {
            List<ActivoDuplicado> l = duplicadoDao.bloquear(List.of(idCaso));
            if (l.isEmpty()) throw new ReglaNegocioException("El caso no existe.");
            ActivoDuplicado d = l.get(0);
            Activo a = d.getActivo();
            codigo[0] = a.getCodigo();
            boolean movidoConError = ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())
                    && EnvioDuplicadosService.ERROR.equals(d.getEstadoEnvio()) && d.getFechaEnvio() != null;
            if (!ActivoDuplicado.EN_DUPLICADOS.equals(d.getEstadoDuplicado()) && !movidoConError) {
                if (EnvioDuplicadosService.ENVIADO.equals(d.getEstadoEnvio())) {
                    throw new ReglaNegocioException("El traslado a la oficina de duplicados se está aplicando en el VSIAF. Espere a que se confirme.");
                }
                if (ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())) {
                    throw new ReglaNegocioException("El bien todavía no llegó a la oficina de duplicados: si se registró por error, anule el caso.");
                }
                throw new ReglaNegocioException("El caso ya está cerrado (" + d.getEstadoDuplicado() + ").");
            }

            String resultado;
            if (!Activo.ESTADO_ACTIVO.equals(a.getEstado())) {
                resultado = "Caso cerrado. El bien ya no está vigente (" + a.getEstado() + "): no se movió.";
            } else if (!a.enDuplicados()) {
                // Lo movieron en el VSIAF por fuera: se cierra el caso sin mover nada.
                resultado = "Caso cerrado. El bien ya estaba fuera de la oficina de duplicados ("
                        + (a.getOficina() != null ? OficinaGestionService.referencia(a.getOficina()) : "—") + "): no se movió.";
            } else {
                resultado = devolver(d, a, req.idResponsableDestino(), justificacion, autor, usuario);
            }
            d.setEstadoDuplicado(ActivoDuplicado.RETIRADO);
            if (!EnvioDuplicadosService.CONFIRMADO.equals(d.getEstadoEnvio())) d.setEstadoEnvio(null);
            d.setFechaRetiro(LocalDateTime.now());
            d.setUsuarioRetiro(usuario);
            d.setJustificacionRetiro(justificacion);
            if (autor != null) d.setModificacionIdUsuario(autor.getIdUsuario());
            d.setModificacion(new Date());
            return resultado;
        });
        actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_MOVIMIENTO, codigo[0],
                "Retiró " + codigo[0] + " de la oficina de duplicados: " + justificacion, idCaso);
        envioService.emitir(1);
        return mensaje;
    }

    private String devolver(ActivoDuplicado d, Activo a, Long idDestino, String justificacion, Usuario autor, String usuario) {
        if (Boolean.TRUE.equals(a.getBloqueado())) {
            throw new ReglaNegocioException("El activo " + a.getCodigo() + " está bloqueado: desbloquéelo antes de retirarlo.");
        }
        Long id = idDestino != null ? idDestino
                : (d.getResponsableOrigen() != null && !d.getResponsableOrigen().isEsCustodia()
                        && !d.getResponsableOrigen().isEsDuplicados() ? d.getResponsableOrigen().getIdResponsable() : null);
        if (id == null) throw new ReglaNegocioException("Elija a qué responsable vuelve el bien.");
        Responsable destino = responsableService.findByIdWithRelations(id);
        if (destino == null) throw new ReglaNegocioException("El responsable elegido no existe.");
        if (!"ACTIVO".equals(destino.getEstado())) throw new ReglaNegocioException("El responsable elegido ya no está vigente: elija otro.");
        Oficina ofDestino = destino.getOficina();
        String invalido = ReglasCustodia.motivoDestino(ofDestino, destino);
        if (invalido != null) throw new ReglaNegocioException(invalido);
        if (ofDestino == null || !"ACTIVO".equals(ofDestino.getEstado())) {
            throw new ReglaNegocioException("La oficina del responsable elegido no está vigente.");
        }
        Predio predio = a.getOficina().getPredio();
        if (ofDestino.getPredio() == null || !Objects.equals(ofDestino.getPredio().getIdPredio(), predio.getIdPredio())) {
            throw new ReglaNegocioException("El bien vuelve a un responsable del mismo predio (" + predio.getUnidad()
                    + "). Para llevarlo a otro predio, retírelo acá y después transfiéralo.");
        }

        TransferenciaService.ActivoConOrigen aco = new TransferenciaService.ActivoConOrigen(a);
        LocalDate hoy = LocalDate.now();
        boolean cola = actualDbfWriterService.esModoCola();
        a.setOficina(ofDestino);
        a.setResponsable(destino);
        a.setFecMod(hoy);
        a.setUsuMod(usuario);
        a.setFechaUlt(hoy);
        a.setUsuario(usuario);
        a.setApiEstado(Short.valueOf("3"));
        a.setModificacion(new Date());
        if (autor != null) a.setModificacionIdUsuario(autor.getIdUsuario());
        a.setSincVsiaf(cola && !a.esSoloSciaf() ? Activo.SINC_EN_COLA : Activo.SINC_CONFIRMADO);
        a.setSincVsiafMensaje(null);
        a.setSincVsiafFecha(LocalDateTime.now());
        d.setResponsableRetiro(destino);

        String numero = d.getNotificacion() != null ? NotificacionDuplicado.numeroImpreso(d.getNotificacion().getNumero()) : "";
        transferenciaService.registrarTransferencia(List.of(aco), "INTERNA", ofDestino, destino,
                autor != null ? autor.getIdUsuario() : null, usuario,
                recortar(("Retiro de duplicados " + numero).trim(), 100),
                recortar("Retiro de la oficina de duplicados: " + justificacion, 1000), null);

        // Último paso: si la orden no se puede dejar, se revierte todo.
        actualDbfWriterService.actualizarLoteTransferencias(List.of(a), predio.getEntidad().getEntidadCodigo(), predio.getUnidad(), usuario);

        String nombre = destino.getPersona() != null ? destino.getPersona().getNombreCompleto() : destino.getCodigoFuncionario();
        return "El bien volvió a " + nombre + " (" + OficinaGestionService.referencia(ofDestino) + ")"
                + (a.esSoloSciaf() ? " (solo SCIAF: no va al VSIAF)." : cola ? "; el VSIAF lo aplica en unos segundos." : ".");
    }

    /**
     * Anula un caso registrado por error <b>antes</b> de que el bien se mueva: la notificación
     * queda anulada y, si se había cerrado un faltante, se reabre. Con el bien ya en la
     * oficina de duplicados, lo que corresponde es retirarlo.
     */
    public String anular(Long idCaso, String justificacionTexto, Usuario autor) {
        String justificacion = textoValido(justificacionTexto, "el motivo de la anulación");
        String usuario = autor != null ? autor.getUsuario() : "SISTEMA";
        String[] codigo = new String[1];
        String mensaje = enTransaccion(() -> {
            List<ActivoDuplicado> l = duplicadoDao.bloquear(List.of(idCaso));
            if (l.isEmpty()) throw new ReglaNegocioException("El caso no existe.");
            ActivoDuplicado d = l.get(0);
            codigo[0] = d.getActivo().getCodigo();
            if (!ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado()) || d.getFechaEnvio() != null) {
                throw new ReglaNegocioException(ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())
                        ? "El bien ya se movió a la oficina de duplicados: para deshacerlo, retírelo con su justificación."
                        : "Solo se anula un caso cuyo bien todavía no se movió.");
            }
            // Solo cuenta la orden que pudo dejar un intento de traslado de este caso (el traslado
            // dura segundos): otras órdenes del bien (una transferencia común) no traban la anulación.
            if (d.getIntentoEnvio() != null && !d.getActivo().esSoloSciaf()
                    && colaDao.findFirstByIdActivoAndTablaAndFechaEncoladoBetweenOrderByIdOrdenDesc(d.getActivo().getIdActivo(),
                            "ACTUAL", d.getIntentoEnvio().minusSeconds(5), d.getIntentoEnvio().plusSeconds(120)).isPresent()) {
                throw new ReglaNegocioException("Un intento de traslado de este bien dejó una orden en la cola del VSIAF: anularlo "
                        + "dejaría el VSIAF distinto del SCIAF. Pulse Reintentar: si el VSIAF ya lo movió, el caso se confirma "
                        + "solo y después puede retirarlo con su justificación.");
            }
            LocalDateTime ahora = LocalDateTime.now();
            d.setEstadoDuplicado(ActivoDuplicado.ANULADO);
            d.setEstadoEnvio(null);
            d.setMensajeEnvio("Anulado antes del traslado.");
            d.setFechaRetiro(ahora);
            d.setUsuarioRetiro(usuario);
            d.setJustificacionRetiro(justificacion);
            if (autor != null) d.setModificacionIdUsuario(autor.getIdUsuario());
            d.setModificacion(new Date());

            NotificacionDuplicado n = d.getNotificacion();
            if (n != null) {
                n.setEstadoNotificacion(NotificacionDuplicado.ANULADA);
                n.setMotivoAnulacion(justificacion);
                n.setFechaAnulacion(ahora);
                n.setUsuarioAnulacion(usuario);
            }
            return reabrirFaltante(d, usuario);
        });
        actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_ELIMINACION, codigo[0],
                "Anuló el registro de " + codigo[0] + " como duplicado: " + justificacion, idCaso);
        envioService.emitir(1);
        return mensaje;
    }

    /** Devuelve el faltante que se cerró al registrar a como estaba, si nadie abrió otro mientras tanto. */
    private String reabrirFaltante(ActivoDuplicado d, String usuario) {
        HallazgoInventario h = d.getHallazgoFaltante();
        if (h == null) return "Caso anulado. El bien no se movió.";
        if (!ControlActivosService.RESUELTO.equals(h.getEstadoHallazgo()) || !RESOLUCION_DUPLICADO.equals(h.getTipoResolucion())) {
            return "Caso anulado. El faltante que se había cerrado ya cambió después: revíselo en Faltantes.";
        }
        if (!hallazgoDao.pendientesDelActivo(d.getActivo().getIdActivo()).isEmpty()) {
            return "Caso anulado. El bien ya tiene otro faltante abierto: el anterior queda cerrado.";
        }
        h.setEstadoHallazgo(d.getActivo().enCustodia() ? ControlActivosService.EN_CUSTODIA : ControlActivosService.ABIERTO);
        h.setTipoResolucion(null);
        h.setFechaResolucion(null);
        if (d.getFaltanteEstadoEnvio() != null) {
            h.setEstadoEnvio(d.getFaltanteEstadoEnvio());
            if (EnvioCustodiaService.ERROR.equals(d.getFaltanteEstadoEnvio())) {
                h.setMensajeEnvio("Reabierto al anular el duplicado: reintente el traslado a la oficina de faltantes.");
            }
        }
        h.setAccionCorrectiva(recortar((h.getAccionCorrectiva() != null ? h.getAccionCorrectiva() + " — " : "")
                + "Reabierto el " + LocalDate.now() + " por " + usuario + ": se anuló el registro como duplicado.", MAX_TEXTO));
        if (h.getInventario() != null) controlActivosService.recalcularFaltantesDe(h.getInventario());
        return "Caso anulado. El bien no se movió y su faltante volvió a quedar abierto.";
    }

    public void reintentar(Long idCaso, Usuario autor) {
        envioService.reintentar(idCaso, autor);
    }

    // ── Detalle y notificación ──────────────────────────────────────────────

    public Map<String, Object> detalle(Long idCaso) {
        return enTransaccion(() -> {
            ActivoDuplicado d = duplicadoDao.findById(idCaso).orElseThrow(() -> new ReglaNegocioException("El caso no existe."));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("idDuplicado", d.getIdDuplicado());
            m.put("estado", d.getEstadoDuplicado());
            m.put("estadoEnvio", d.getEstadoEnvio());
            m.put("mensajeEnvio", d.getMensajeEnvio());
            m.put("motivo", d.getMotivo());
            m.put("registro", d.getRegistro());
            m.put("usuarioRegistro", d.getUsuarioRegistro());
            m.put("fechaEnvio", d.getFechaEnvio());
            m.put("fechaRetiro", d.getFechaRetiro());
            m.put("usuarioRetiro", d.getUsuarioRetiro());
            m.put("justificacionRetiro", d.getJustificacionRetiro());
            m.put("retiradoA", d.getResponsableRetiro() != null && d.getResponsableRetiro().getPersona() != null
                    ? d.getResponsableRetiro().getPersona().getNombreCompleto() : null);
            m.put("sobrante", repo.fichaPorId(d.getActivo().getIdActivo()));
            m.put("principal", repo.fichaPorId(d.getActivoPrincipal().getIdActivo()));
            m.put("persona", d.getPersona() != null ? d.getPersona().getNombreCompleto() : null);
            m.put("oficinaOrigen", d.getOficinaOrigen() != null ? OficinaGestionService.referencia(d.getOficinaOrigen()) : null);
            m.put("destino", d.getResponsableCustodia() != null && d.getResponsableCustodia().getOficina() != null
                    ? OficinaGestionService.referencia(d.getResponsableCustodia().getOficina()) : null);
            NotificacionDuplicado n = d.getNotificacion();
            m.put("notificacion", n != null ? NotificacionDuplicado.numeroImpreso(n.getNumero()) : null);
            m.put("notificacionEstado", n != null ? n.getEstadoNotificacion() : null);
            HallazgoInventario h = d.getHallazgoFaltante();
            m.put("faltante", h != null && h.getActa() != null ? ActaFaltante.numeroImpreso(h.getActa().getNumero())
                    : h != null ? "faltante de levantamiento" : null);
            boolean movidoConError = ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())
                    && EnvioDuplicadosService.ERROR.equals(d.getEstadoEnvio()) && d.getFechaEnvio() != null;
            m.put("puedeRetirar", ActivoDuplicado.EN_DUPLICADOS.equals(d.getEstadoDuplicado()) || movidoConError);
            m.put("puedeAnular", ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado()) && d.getFechaEnvio() == null);
            m.put("puedeReintentar", ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())
                    && EnvioDuplicadosService.ERROR.equals(d.getEstadoEnvio()));
            return m;
        });
    }

    public DuplicadosDTOs.Notificacion notificacionDelCaso(Long idCaso) {
        return enTransaccion(() -> {
            ActivoDuplicado d = duplicadoDao.findById(idCaso).orElseThrow(() -> new ReglaNegocioException("El caso no existe."));
            if (d.getNotificacion() == null) throw new ReglaNegocioException("El caso no tiene notificación.");
            return vista(d.getNotificacion(), d);
        });
    }

    /** Para la página pública del QR; null si el token no existe. */
    public DuplicadosDTOs.Notificacion porToken(String token) {
        if (token == null || token.isBlank() || token.length() > 40) return null;
        return enTransaccion(() -> notificacionDao.findByToken(token.trim())
                .map(n -> vista(n, duplicadoDao.findFirstByNotificacionIdNotificacion(n.getIdNotificacion()).orElse(null)))
                .orElse(null));
    }

    private DuplicadosDTOs.Notificacion vista(NotificacionDuplicado n, ActivoDuplicado d) {
        DuplicadosDTOs.Contenido c;
        try {
            c = json.readValue(n.getContenido(), DuplicadosDTOs.Contenido.class);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo leer la notificación " + n.getNumero() + ": " + e.getMessage(), e);
        }
        return new DuplicadosDTOs.Notificacion(n.getIdNotificacion(), n.getNumero(),
                NotificacionDuplicado.numeroImpreso(n.getNumero()), n.getToken(), n.getHashContenido(),
                n.getEstadoNotificacion(), n.getFechaAnulacion(), n.getMotivoAnulacion(),
                d != null ? d.getEstadoDuplicado() : null, d != null ? d.getFechaRetiro() : null,
                d != null && ActivoDuplicado.RETIRADO.equals(d.getEstadoDuplicado()) ? d.getJustificacionRetiro() : null,
                Objects.equals(sha256(n.getContenido()), n.getHashContenido()), c);
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private static DuplicadosDTOs.BienDoc bienDoc(Activo a) {
        DatosTecnicos dt = DatosTecnicos.de(a.getDescripcion());
        Oficina o = a.getOficina();
        Responsable r = a.getResponsable();
        return new DuplicadosDTOs.BienDoc(a.getCodigo(), a.getDescripcion(), dt.descripcion(), dt.marca(), dt.modelo(), dt.serie(),
                o != null ? (o.getCodOfi() != null ? o.getCodOfi() + " – " : "") + o.getNombre() : null,
                o != null && o.getPredio() != null ? o.getPredio().getUnidad() : null,
                r != null && r.getPersona() != null ? r.getPersona().getNombreCompleto() : null);
    }

    /** Nombre de quien registra, para el papel: su persona o, si no tiene, su usuario. */
    private String nombreDe(Usuario autor) {
        if (autor == null || autor.getIdUsuario() == null) return "SISTEMA";
        return usuarioDao.findById(autor.getIdUsuario())
                .map(u -> u.getPersona() != null && u.getPersona().getNombreCompleto() != null
                        && !u.getPersona().getNombreCompleto().isBlank()
                        ? u.getPersona().getNombreCompleto().trim() : u.getUsuario())
                .orElse(autor.getUsuario());
    }

    private static String textoValido(String texto, String que) {
        String t = limpio(texto);
        if (t == null || t.length() < MIN_MOTIVO) {
            throw new ReglaNegocioException("Escriba " + que + " (al menos " + MIN_MOTIVO + " caracteres).");
        }
        if (t.length() > MAX_TEXTO) throw new ReglaNegocioException("El texto admite hasta " + MAX_TEXTO + " caracteres.");
        // Texto libre largo: va como lo escribió (la regla de mayúsculas no aplica, como en la observación de una transferencia).
        return t;
    }

    private String aJson(DuplicadosDTOs.Contenido c) {
        try {
            return json.writeValueAsString(c);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo armar la notificación: " + e.getMessage(), e);
        }
    }

    private String nuevoToken() {
        byte[] b = new byte[24];
        azar.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String sha256(String texto) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((texto == null ? "" : texto).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String limpio(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String recortar(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    private <T> T enTransaccion(Supplier<T> trabajo) {
        return new TransactionTemplate(txManager).execute(estado -> trabajo.get());
    }
}
