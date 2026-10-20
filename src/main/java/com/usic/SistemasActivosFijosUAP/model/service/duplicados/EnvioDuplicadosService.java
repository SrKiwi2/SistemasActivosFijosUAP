package com.usic.SistemasActivosFijosUAP.model.service.duplicados;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.componet.VsiafDisponibilidad;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDuplicadoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.ActivoDuplicado;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;
import com.usic.SistemasActivosFijosUAP.model.entity.NotificacionDuplicado;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.TransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.OficinaGestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Traslado del registro sobrante a la oficina de duplicados de su predio (transferencia
 * interna), en pasos anotados en {@code activo_duplicado.estado_envio}, igual que el envío a
 * la custodia de faltantes:
 * <ol>
 *   <li>{@link #ESPERANDO_ALTA}: la notificación ya existe; se asegura la oficina y la persona
 *       en ella y se espera que el worker del VSIAF confirme su alta (el worker aplica
 *       {@code ACTUAL_…} antes que {@code OFICINA_…} y {@code RESP_…}).</li>
 *   <li>{@link #ENVIADO}: el bien se movió en el SCIAF (transferencia interna con historial) y
 *       su UPDATE de ACTUAL está en la cola.</li>
 *   <li>{@link #CONFIRMADO}: el worker lo aplicó; el caso pasa a {@code EN_DUPLICADOS}.</li>
 * </ol>
 * {@link #ERROR} guarda el motivo y se reintenta a mano. Un ciclo cada 30 s avanza lo que espera.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvioDuplicadosService {

    public static final String ESPERANDO_ALTA = "ESPERANDO_ALTA";
    public static final String ENVIADO        = "ENVIADO";
    public static final String CONFIRMADO     = "CONFIRMADO";
    public static final String ERROR          = "ERROR";

    static final String EVENTO = "duplicados";
    private static final List<String> ROLES_AVISO = List.of("ADMINISTRADOR", "SUPER USUARIO");

    private final IActivoDuplicadoDao duplicadoDao;
    private final com.usic.SistemasActivosFijosUAP.model.dao.IHallazgoInventarioDao hallazgoDao;
    private final IResponsableService responsableService;
    private final IDbfColaOrdenDao colaDao;
    private final IUsuarioDao usuarioDao;
    private final CustodiaDuplicadosService custodiaService;
    private final TransferenciaService transferenciaService;
    private final ActualDbfWriterService actualDbfWriterService;
    private final ActividadService actividadService;
    private final SseEmitterRegistry sse;
    private final PlatformTransactionManager txManager;
    private final VsiafDisponibilidad vsiaf;

    // ── Entrada ─────────────────────────────────────────────────────────────

    /** Prepara el destino y traslada lo que ya se pueda. Nunca lanza: lo que falle queda anotado. */
    public void iniciar(Collection<Long> idsCaso, Usuario autor) {
        prepararDestinos(idsCaso, autor);
        despachar(idsCaso);
    }

    /**
     * Vuelve a intentar un caso en ERROR. Si el bien ya se había movido en el SCIAF, solo se
     * reencola su traslado; si no, se reintenta desde el alta.
     */
    public void reintentar(Long idCaso, Usuario autor) {
        Boolean soloTraslado = enTransaccion(() -> {
            List<ActivoDuplicado> l = duplicadoDao.bloquear(List.of(idCaso));
            if (l.isEmpty()) throw new ReglaNegocioException("El caso no existe.");
            ActivoDuplicado d = l.get(0);
            if (!ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado()) || !ERROR.equals(d.getEstadoEnvio())) {
                throw new ReglaNegocioException("Solo se reintenta un caso cuyo traslado quedó con error.");
            }
            if (d.getFechaEnvio() != null) return true;
            d.setEstadoEnvio(ESPERANDO_ALTA);
            d.setMensajeEnvio(null);
            d.setResponsableCustodia(null);
            return false;
        });
        if (Boolean.TRUE.equals(soloTraslado)) reencolarTraslado(idCaso, autor);
        else iniciar(List.of(idCaso), autor);
    }

    // ── Ciclo ───────────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${duplicados.envio.interval.ms:30000}", initialDelay = 75000,
            scheduler = "custodiaTaskScheduler")
    public void ciclo() {
        if (!vsiaf.dbf("envío de duplicados")) return;
        try {
            List<Long> esperando = enTransaccion(() -> duplicadoDao.idsConEnvioEn(List.of(ESPERANDO_ALTA)));
            if (!esperando.isEmpty()) iniciar(esperando, null);
        } catch (Exception e) {
            log.error("[DUPLICADOS] Falló el ciclo de envío: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelayString = "${duplicados.confirmacion.interval.ms:10000}", initialDelay = 20000,
            scheduler = "custodiaTaskScheduler")
    public void confirmarPendientes() {
        if (!actualDbfWriterService.esModoCola()) return;
        try {
            confirmarTraslados();
        } catch (Exception e) {
            log.error("[DUPLICADOS] Falló la confirmación de traslados: {}", e.getMessage(), e);
        }
    }

    // ── Paso 1: destino ─────────────────────────────────────────────────────

    private record Grupo(Long idResponsableOrigen, Long idPersona) {}

    private void prepararDestinos(Collection<Long> idsCaso, Usuario autor) {
        Map<Grupo, List<Long>> porGrupo = enTransaccion(() -> {
            Map<Grupo, List<Long>> m = new LinkedHashMap<>();
            for (ActivoDuplicado d : duplicadoDao.findAllById(idsCaso)) {
                if (!ESPERANDO_ALTA.equals(d.getEstadoEnvio()) || !ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())) continue;
                if (d.getResponsableCustodia() != null) continue;
                if (d.getResponsableOrigen() == null) {
                    anotar(List.of(d.getIdDuplicado()), ERROR, "El caso no tiene responsable de origen.");
                    continue;
                }
                // Si la persona se unificó con otra después de registrar, el destino es el de la vigente
                // (el caso se actualiza abajo, con el caso bloqueado).
                com.usic.SistemasActivosFijosUAP.model.entity.Persona vigente =
                        custodiaService.personaVigente(d.getPersona() != null ? d.getPersona().getIdPersona() : null);
                m.computeIfAbsent(new Grupo(d.getResponsableOrigen().getIdResponsable(),
                        vigente != null ? vigente.getIdPersona() : null), k -> new ArrayList<>())
                        .add(d.getIdDuplicado());
            }
            return m;
        });

        porGrupo.forEach((grupo, ids) -> {
            try {
                CustodiaDuplicadosService.Custodia c = custodiaService.asegurar(grupo.idResponsableOrigen(), grupo.idPersona(), autor);
                enTransaccion(() -> {
                    Responsable custodio = responsableService.findById(c.idResponsable());
                    for (ActivoDuplicado d : duplicadoDao.bloquear(ids)) {
                        if (!ESPERANDO_ALTA.equals(d.getEstadoEnvio()) || !ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())) continue;
                        if (grupo.idPersona() != null && (d.getPersona() == null || !grupo.idPersona().equals(d.getPersona().getIdPersona()))) {
                            d.setPersona(custodiaService.personaVigente(grupo.idPersona()));
                        }
                        d.setResponsableCustodia(custodio);
                        d.setMensajeEnvio(c.vsiafOk() ? null : c.mensaje());
                    }
                    return null;
                });
            } catch (ReglaNegocioException e) {
                anotar(ids, ERROR, e.getMessage());
            } catch (Exception e) {
                // Montaje caído o similar: sigue ESPERANDO_ALTA y el ciclo vuelve a intentar.
                log.warn("[DUPLICADOS] No se pudo preparar el destino del responsable {}: {}", grupo.idResponsableOrigen(), e.getMessage());
                anotar(ids, ESPERANDO_ALTA, "No se pudo preparar la oficina de duplicados: " + e.getMessage() + ". Se reintenta solo.");
            }
        });
    }

    // ── Paso 2: traslado ────────────────────────────────────────────────────

    private void despachar(Collection<Long> idsCaso) {
        Map<Long, List<Long>> porCustodio = enTransaccion(() -> {
            Map<Long, List<Long>> m = new LinkedHashMap<>();
            for (ActivoDuplicado d : duplicadoDao.findAllById(idsCaso)) {
                if (!ESPERANDO_ALTA.equals(d.getEstadoEnvio()) || d.getResponsableCustodia() == null) continue;
                if (!ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())) continue;
                m.computeIfAbsent(d.getResponsableCustodia().getIdResponsable(), k -> new ArrayList<>()).add(d.getIdDuplicado());
            }
            return m;
        });

        porCustodio.forEach((idCustodio, ids) -> {
            VsiafApoyoService.EstadoVsiaf estado = custodiaService.estadoEnVsiaf(idCustodio);
            switch (estado.codigo()) {
                // De a uno: cada orden de ACTUAL queda en la cola en el momento (REQUIRES_NEW); si
                // un lote fallara a la mitad, las órdenes ya escritas quedarían sin su caso movido.
                case VsiafApoyoService.EST_VSIAF -> ids.forEach(id -> trasladar(List.of(id), idCustodio));
                case VsiafApoyoService.EST_ERROR -> anotar(ids, ERROR, "El VSIAF no aceptó la oficina de duplicados: " + estado.detalle());
                // El alta no llegó a encolarse: se suelta y el próximo ciclo la vuelve a pedir.
                case VsiafApoyoService.EST_PENDIENTE -> soltarDestino(ids);
                default -> anotar(ids, ESPERANDO_ALTA, "Esperando que el VSIAF confirme la oficina de duplicados. " + estado.detalle());
            }
        });
    }

    private void soltarDestino(List<Long> ids) {
        enTransaccion(() -> {
            for (ActivoDuplicado d : duplicadoDao.bloquear(ids)) {
                if (!ESPERANDO_ALTA.equals(d.getEstadoEnvio())) continue;
                d.setResponsableCustodia(null);
                d.setMensajeEnvio("La oficina de duplicados no se pudo enviar al VSIAF; se reintenta solo.");
            }
            return null;
        });
    }

    private record Resultado(Usuario autor, String referencia, String numeros, int cantidad) {}

    private void trasladar(List<Long> ids, Long idCustodio) {
        List<String> avisos = new ArrayList<>();
        // En su propia transacción, antes de encolar: queda aunque el traslado se deshaga.
        enTransaccion(() -> {
            LocalDateTime ahora = LocalDateTime.now();
            for (ActivoDuplicado d : duplicadoDao.bloquear(ids)) {
                if (ESPERANDO_ALTA.equals(d.getEstadoEnvio()) && ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())) {
                    d.setIntentoEnvio(ahora);
                }
            }
            return null;
        });
        try {
            Resultado r = enTransaccion(() -> trasladarEnTransaccion(ids, idCustodio, avisos));
            if (r != null) {
                actividadService.registrar(r.autor(), ActividadService.MOD_TRANSFERENCIA, ActividadService.ACC_MOVIMIENTO,
                        r.referencia(), "Envío a la oficina de duplicados (" + r.numeros() + "): " + r.cantidad()
                                + " bien(es) a " + r.referencia(), r.cantidad(), null);
                emitir(r.cantidad());
            }
        } catch (Exception e) {
            log.warn("[DUPLICADOS] No se pudo trasladar al destino {}: {}", idCustodio, e.getMessage());
            anotar(ids, ESPERANDO_ALTA, "No se pudo encolar el traslado: " + e.getMessage() + ". Se reintenta solo.");
        }
        avisos.forEach(a -> log.info("[DUPLICADOS] {}", a));
    }

    private Resultado trasladarEnTransaccion(List<Long> ids, Long idCustodio, List<String> avisos) {
        Responsable custodio = responsableService.findByIdWithRelations(idCustodio);
        Oficina ofDestino = custodio.getOficina();
        Predio predio = ofDestino.getPredio();
        boolean cola = actualDbfWriterService.esModoCola();
        LocalDateTime ahora = LocalDateTime.now();

        List<TransferenciaService.ActivoConOrigen> acos = new ArrayList<>();
        List<ActivoDuplicado> movidos = new ArrayList<>();
        List<String> numeros = new ArrayList<>();
        ActivoDuplicado primero = null;

        for (ActivoDuplicado d : duplicadoDao.bloquear(ids)) {
            // Se relee con el bloqueo: pudo anularse mientras tanto.
            if (!ESPERANDO_ALTA.equals(d.getEstadoEnvio()) || !ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())) continue;
            Activo a = d.getActivo();
            if (a.getResponsable() != null && Objects.equals(a.getResponsable().getIdResponsable(), idCustodio)) {
                // Ya está en su destino (un intento anterior dejó la orden y el VSIAF la aplicó).
                d.setEstadoEnvio(CONFIRMADO);
                d.setEstadoDuplicado(ActivoDuplicado.EN_DUPLICADOS);
                d.setFechaEnvio(ahora);
                d.setMensajeEnvio("Ya estaba en la oficina de duplicados: se confirmó sin otra orden.");
                continue;
            }
            String problema = problemaParaTrasladar(d, a, predio);
            if (problema != null) {
                d.setEstadoEnvio(ERROR);
                d.setMensajeEnvio(problema);
                avisos.add(a.getCodigo() + ": " + problema);
                continue;
            }
            acos.add(new TransferenciaService.ActivoConOrigen(a));
            movidos.add(d);
            NotificacionDuplicado n = d.getNotificacion();
            if (n != null) numeros.add(NotificacionDuplicado.numeroImpreso(n.getNumero()));
            if (primero == null) primero = d;
        }
        if (acos.isEmpty()) return null;

        Usuario autor = primero.getRegistroIdUsuario() != null
                ? usuarioDao.findById(primero.getRegistroIdUsuario()).orElse(null) : null;
        String usuario = primero.getUsuarioRegistro() != null ? primero.getUsuarioRegistro() : "SISTEMA";
        LocalDate hoy = LocalDate.now();

        for (TransferenciaService.ActivoConOrigen ac : acos) {
            Activo a = ac.activo;
            a.setOficina(ofDestino);
            a.setResponsable(custodio);
            a.setFecMod(hoy);
            a.setUsuMod(usuario);
            a.setFechaUlt(hoy);
            a.setUsuario(usuario);
            a.setApiEstado(Short.valueOf("3"));
            a.setModificacion(new Date());
            if (autor != null) a.setModificacionIdUsuario(autor.getIdUsuario());
            a.setSincVsiaf(cola && !a.esSoloSciaf() ? Activo.SINC_EN_COLA : Activo.SINC_CONFIRMADO);
            a.setSincVsiafMensaje(null);
            a.setSincVsiafFecha(ahora);
        }

        String lista = String.join(", ", numeros);
        transferenciaService.registrarTransferencia(acos, "INTERNA", ofDestino, custodio,
                autor != null ? autor.getIdUsuario() : null, usuario,
                recortar("Notificación de duplicado " + lista, 100),
                "Envío a la oficina de duplicados (registro duplicado del revalúo)", null);

        // Último paso: si la orden no se puede dejar, la excepción deshace todo lo anterior.
        actualDbfWriterService.actualizarLoteTransferencias(acos.stream().map(ac -> ac.activo).toList(),
                predio.getEntidad().getEntidadCodigo(), predio.getUnidad(), usuario);

        for (ActivoDuplicado d : movidos) {
            d.setFechaEnvio(ahora);
            d.setMensajeEnvio(null);
            if (cola && !d.getActivo().esSoloSciaf()) {
                d.setEstadoEnvio(ENVIADO);
            } else {
                // Sin worker (modo bytes) la escritura ya se hizo; un bien solo del SCIAF no genera orden.
                d.setEstadoEnvio(CONFIRMADO);
                d.setEstadoDuplicado(ActivoDuplicado.EN_DUPLICADOS);
            }
        }
        return new Resultado(autor, OficinaGestionService.referencia(ofDestino), lista, movidos.size());
    }

    /** Por qué este bien no puede trasladarse ahora; null si puede. */
    private String problemaParaTrasladar(ActivoDuplicado d, Activo a, Predio predioDestino) {
        if (!Activo.ESTADO_ACTIVO.equals(a.getEstado())) return "El bien ya no está vigente.";
        if (Boolean.TRUE.equals(a.getBloqueado())) return "El bien está bloqueado: desbloquéelo y reintente.";
        if (a.enDuplicados()) return "El bien ya está en una oficina de duplicados.";
        if (!hallazgoDao.pendientesDelActivo(a.getIdActivo()).isEmpty()) {
            return "El bien tiene un faltante abierto (lo registraron como faltante después). Anule el caso o resuelva ese faltante.";
        }
        Responsable actual = a.getResponsable();
        Long idPersonaActual = actual != null && actual.getPersona() != null ? actual.getPersona().getIdPersona() : null;
        com.usic.SistemasActivosFijosUAP.model.entity.Persona vigente =
                custodiaService.personaVigente(d.getPersona() != null ? d.getPersona().getIdPersona() : null);
        Long idPersonaCaso = vigente != null ? vigente.getIdPersona() : null;
        if (!Objects.equals(idPersonaActual, idPersonaCaso)) {
            return "El bien cambió de responsable después de registrar el duplicado. Anule el caso y regístrelo de nuevo.";
        }
        Long idPredioBien = a.getOficina() != null && a.getOficina().getPredio() != null
                ? a.getOficina().getPredio().getIdPredio() : null;
        if (!Objects.equals(idPredioBien, predioDestino.getIdPredio())) {
            return "El bien está en otro predio que la oficina de duplicados elegida.";
        }
        return null;
    }

    /** Reintento de un traslado que el VSIAF rechazó: el bien ya está en la oficina de duplicados del SCIAF. */
    private void reencolarTraslado(Long idCaso, Usuario autor) {
        try {
            enTransaccion(() -> {
                String usuario = autor != null ? autor.getUsuario() : "SISTEMA";
                List<ActivoDuplicado> l = duplicadoDao.bloquear(List.of(idCaso));
                ActivoDuplicado d = l.get(0);
                Activo a = d.getActivo();
                Predio p = a.getOficina().getPredio();
                actualDbfWriterService.actualizarLoteTransferencias(List.of(a), p.getEntidad().getEntidadCodigo(), p.getUnidad(), usuario);
                boolean esperaWorker = actualDbfWriterService.esModoCola() && !a.esSoloSciaf();
                d.setFechaEnvio(LocalDateTime.now());
                d.setMensajeEnvio(null);
                d.setEstadoEnvio(esperaWorker ? ENVIADO : CONFIRMADO);
                if (!esperaWorker) d.setEstadoDuplicado(ActivoDuplicado.EN_DUPLICADOS);
                return null;
            });
        } catch (Exception e) {
            anotar(List.of(idCaso), ERROR, "No se pudo reenviar el traslado: " + e.getMessage());
        }
    }

    // ── Paso 3: confirmación del worker ─────────────────────────────────────

    private void confirmarTraslados() {
        int confirmados = enTransaccion(() -> {
            int n = 0;
            List<Long> ids = duplicadoDao.idsConEnvioEn(List.of(ENVIADO, ERROR));
            if (ids.isEmpty()) return 0;
            // Bloqueados: un retiro o una anulación al mismo tiempo no se pisan con esto.
            for (ActivoDuplicado d : duplicadoDao.bloquear(ids)) {
                if (!ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())) continue;
                if (!ENVIADO.equals(d.getEstadoEnvio()) && !ERROR.equals(d.getEstadoEnvio())) continue;
                if (d.getFechaEnvio() == null) continue;   // ERROR de antes de trasladar
                if (d.getActivo().esSoloSciaf()) {
                    d.setEstadoEnvio(CONFIRMADO);
                    d.setEstadoDuplicado(ActivoDuplicado.EN_DUPLICADOS);
                    d.setMensajeEnvio(null);
                    n++;
                    continue;
                }
                Optional<DbfColaOrden> orden = colaDao
                        .findFirstByIdActivoAndTablaAndFechaEncoladoGreaterThanEqualOrderByIdOrdenDesc(
                                d.getActivo().getIdActivo(), "ACTUAL", d.getFechaEnvio().minusSeconds(5));
                if (orden.isEmpty()) continue;
                String estado = orden.get().getEstado();
                if (DbfColaOrden.OK.equals(estado)) {
                    d.setEstadoEnvio(CONFIRMADO);
                    d.setEstadoDuplicado(ActivoDuplicado.EN_DUPLICADOS);
                    d.setMensajeEnvio(null);
                    n++;
                } else if (DbfColaOrden.ERROR.equals(estado) && ENVIADO.equals(d.getEstadoEnvio())) {
                    d.setEstadoEnvio(ERROR);
                    d.setMensajeEnvio("El VSIAF rechazó el traslado: "
                            + (orden.get().getMensaje() != null ? orden.get().getMensaje() : "sin motivo"));
                }
            }
            return n;
        });
        if (confirmados > 0) {
            log.info("[DUPLICADOS] {} duplicado(s) confirmados en la oficina de duplicados", confirmados);
            emitir(confirmados);
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private void anotar(Collection<Long> ids, String estadoEnvio, String mensaje) {
        try {
            enTransaccion(() -> {
                for (ActivoDuplicado d : duplicadoDao.bloquear(ids)) {
                    if (CONFIRMADO.equals(d.getEstadoEnvio()) || d.getEstadoEnvio() == null) continue;
                    if (!ActivoDuplicado.EN_PROCESO.equals(d.getEstadoDuplicado())) continue;
                    d.setEstadoEnvio(estadoEnvio);
                    d.setMensajeEnvio(recortar(mensaje, 2000));
                }
                return null;
            });
        } catch (Exception e) {
            log.error("[DUPLICADOS] No se pudo anotar el estado de envío: {}", e.getMessage(), e);
        }
    }

    void emitir(int cantidad) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cantidad", cantidad);
        try {
            sse.broadcast(EVENTO, payload);
            sse.enviarARoles(ROLES_AVISO, EVENTO, payload);
        } catch (Exception e) {
            log.warn("[DUPLICADOS] No se pudo emitir el evento: {}", e.getMessage());
        }
    }

    private static String recortar(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    private <T> T enTransaccion(Supplier<T> trabajo) {
        return new TransactionTemplate(txManager).execute(estado -> trabajo.get());
    }
}
