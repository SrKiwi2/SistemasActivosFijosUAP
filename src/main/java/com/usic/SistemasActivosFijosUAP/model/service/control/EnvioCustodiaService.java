package com.usic.SistemasActivosFijosUAP.model.service.control;

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
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IHallazgoInventarioDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IUsuarioDao;
import com.usic.SistemasActivosFijosUAP.model.entity.ActaFaltante;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;
import com.usic.SistemasActivosFijosUAP.model.entity.HallazgoInventario;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.TransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.OficinaGestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Traslado de los faltantes registrados a la custodia del predio (transferencia interna).
 * <p>
 * El worker del VSIAF aplica la cola por nombre de archivo y {@code ACTUAL_…} va antes que
 * {@code OFICINA_…} y {@code RESP_…}: si el traslado se encolara junto con el alta del
 * responsable de custodia, el VSIAF movería el bien antes de que ese responsable exista.
 * Por eso el traslado va en pasos, cada uno anotado en {@code hallazgo.estado_envio}:
 * <ol>
 *   <li>{@link #ESPERANDO_ALTA}: el acta ya está emitida; se espera que el worker confirme
 *       la oficina y el responsable de custodia.</li>
 *   <li>{@link #ENVIADO}: confirmada la custodia, el bien se mueve en el SCIAF (queda una
 *       transferencia interna con historial) y se encola el UPDATE de ACTUAL.</li>
 *   <li>{@link #CONFIRMADO}: el worker aplicó el traslado; el faltante pasa a EN_CUSTODIA.</li>
 * </ol>
 * {@link #ERROR} guarda el motivo en {@code mensaje_envio} y se reintenta a mano.
 * Un ciclo cada 30 s avanza lo que esté esperando.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvioCustodiaService {

    public static final String ESPERANDO_ALTA = "ESPERANDO_ALTA";
    public static final String ENVIADO        = "ENVIADO";
    public static final String CONFIRMADO     = "CONFIRMADO";
    public static final String ERROR          = "ERROR";

    private static final List<String> ROLES_AVISO = List.of("ADMINISTRADOR", "SUPER USUARIO");

    private final IHallazgoInventarioDao hallazgoDao;
    private final IResponsableService responsableService;
    private final IDbfColaOrdenDao colaDao;
    private final IUsuarioDao usuarioDao;
    private final CustodiaFaltantesService custodiaService;
    private final TransferenciaService transferenciaService;
    private final ActualDbfWriterService actualDbfWriterService;
    private final ActividadService actividadService;
    private final SseEmitterRegistry sse;
    private final PlatformTransactionManager txManager;

    // ── Entrada: lo llama el registro del acta ─────────────────────────────

    /**
     * Deja lista la custodia de cada responsable de los faltantes y traslada lo que ya se
     * pueda. Nunca lanza: lo que falle queda anotado en el hallazgo y lo retoma el ciclo.
     */
    public void iniciar(Collection<Long> idsHallazgo, Usuario autor) {
        prepararCustodias(idsHallazgo, autor);
        despachar(idsHallazgo);
    }

    /**
     * Vuelve a intentar los faltantes en ERROR de un acta. Si el bien ya se había movido en
     * el SCIAF, solo se reencola su traslado; si no, se reintenta desde el alta.
     */
    public int reintentar(Long idActa, Usuario autor) {
        List<Long> desdeAlta = new ArrayList<>();
        List<Long> soloTraslado = new ArrayList<>();
        enTransaccion(() -> {
            for (HallazgoInventario h : hallazgoDao.deLaActa(idActa)) {
                if (!ERROR.equals(h.getEstadoEnvio())) continue;
                if (!ControlActivosService.ABIERTO.equals(h.getEstadoHallazgo())) continue;   // ya resuelto
                if (yaEstaEnCustodia(h)) soloTraslado.add(h.getIdHallazgo());
                else desdeAlta.add(h.getIdHallazgo());
            }
            return null;
        });
        if (!soloTraslado.isEmpty()) reencolarTraslado(soloTraslado, autor);
        if (!desdeAlta.isEmpty()) {
            enTransaccion(() -> {
                for (HallazgoInventario h : hallazgoDao.bloquear(desdeAlta)) {
                    h.setEstadoEnvio(ESPERANDO_ALTA);
                    h.setMensajeEnvio(null);
                }
                return null;
            });
            iniciar(desdeAlta, autor);
        }
        return soloTraslado.size() + desdeAlta.size();
    }

    // ── Ciclo ──────────────────────────────────────────────────────────────

    @Scheduled(fixedDelayString = "${custodia.envio.interval.ms:30000}", initialDelay = 60000)
    public void ciclo() {
        try {
            List<Long> esperando = enTransaccion(() -> hallazgoDao.conEnvioEn(List.of(ESPERANDO_ALTA))
                    .stream().map(HallazgoInventario::getIdHallazgo).toList());
            if (!esperando.isEmpty()) {
                prepararCustodias(esperando, null);
                despachar(esperando);
            }
            if (actualDbfWriterService.esModoCola()) confirmarTraslados();
        } catch (Exception e) {
            log.error("[CUSTODIA] Falló el ciclo de envío: {}", e.getMessage(), e);
        }
    }

    // ── Paso 1: custodia del predio ─────────────────────────────────────────

    /** A cada faltante sin custodia asignada le busca (o crea) la de su responsable. */
    private void prepararCustodias(Collection<Long> idsHallazgo, Usuario autor) {
        Map<Long, List<Long>> porResponsable = enTransaccion(() -> {
            Map<Long, List<Long>> m = new LinkedHashMap<>();
            for (HallazgoInventario h : hallazgoDao.findAllById(idsHallazgo)) {
                if (!ESPERANDO_ALTA.equals(h.getEstadoEnvio())) continue;
                if (h.getResponsableCustodia() != null) continue;
                if (h.getResponsable() == null) {
                    anotar(List.of(h.getIdHallazgo()), ERROR, "El faltante no tiene a quién imputarse.");
                    continue;
                }
                m.computeIfAbsent(h.getResponsable().getIdResponsable(), k -> new ArrayList<>())
                        .add(h.getIdHallazgo());
            }
            return m;
        });

        porResponsable.forEach((idResponsable, ids) -> {
            try {
                CustodiaFaltantesService.Custodia c = custodiaService.asegurar(idResponsable, autor);
                enTransaccion(() -> {
                    Responsable custodio = responsableService.findById(c.idResponsable());
                    for (HallazgoInventario h : hallazgoDao.bloquear(ids)) {
                        h.setResponsableCustodia(custodio);
                        h.setMensajeEnvio(c.vsiafOk() ? null : c.mensaje());
                    }
                    return null;
                });
            } catch (ReglaNegocioException e) {
                anotar(ids, ERROR, e.getMessage());
            } catch (Exception e) {
                // Montaje caído o similar: queda ESPERANDO_ALTA y el ciclo vuelve a intentar.
                log.warn("[CUSTODIA] No se pudo preparar la custodia del responsable {}: {}", idResponsable, e.getMessage());
                anotar(ids, ESPERANDO_ALTA, "No se pudo preparar la custodia: " + e.getMessage() + ". Se reintenta solo.");
            }
        });
    }

    // ── Paso 2: traslado ────────────────────────────────────────────────────

    /** Traslada los faltantes cuya custodia ya está confirmada en el VSIAF. */
    private void despachar(Collection<Long> idsHallazgo) {
        Map<Long, List<Long>> porCustodio = enTransaccion(() -> {
            Map<Long, List<Long>> m = new LinkedHashMap<>();
            for (HallazgoInventario h : hallazgoDao.findAllById(idsHallazgo)) {
                if (!ESPERANDO_ALTA.equals(h.getEstadoEnvio()) || h.getResponsableCustodia() == null) continue;
                m.computeIfAbsent(h.getResponsableCustodia().getIdResponsable(), k -> new ArrayList<>())
                        .add(h.getIdHallazgo());
            }
            return m;
        });

        porCustodio.forEach((idCustodio, ids) -> {
            VsiafApoyoService.EstadoVsiaf estado = custodiaService.estadoEnVsiaf(idCustodio);
            switch (estado.codigo()) {
                case VsiafApoyoService.EST_VSIAF -> trasladar(ids, idCustodio);
                case VsiafApoyoService.EST_ERROR -> anotar(ids, ERROR,
                        "El VSIAF no aceptó la custodia: " + estado.detalle());
                // PENDIENTE: el alta no llegó a encolarse; asegurar() la reenvía en el próximo ciclo.
                case VsiafApoyoService.EST_PENDIENTE -> reprepararCustodia(ids);
                default -> anotar(ids, ESPERANDO_ALTA, "Esperando que el VSIAF confirme la custodia. " + estado.detalle());
            }
        });
    }

    /** El alta de la custodia no salió: se suelta para que el próximo ciclo la vuelva a pedir. */
    private void reprepararCustodia(List<Long> ids) {
        enTransaccion(() -> {
            for (HallazgoInventario h : hallazgoDao.bloquear(ids)) {
                if (ESPERANDO_ALTA.equals(h.getEstadoEnvio())) {
                    h.setResponsableCustodia(null);
                    h.setMensajeEnvio("La custodia no se pudo enviar al VSIAF; se reintenta solo.");
                }
            }
            return null;
        });
    }

    /**
     * Mueve los bienes a la custodia en el SCIAF, deja la transferencia interna con su
     * historial y encola el UPDATE de ACTUAL, todo en una transacción: si la orden no se
     * puede dejar (montaje caído), no se mueve nada y el ciclo lo vuelve a intentar.
     */
    private void trasladar(List<Long> ids, Long idCustodio) {
        List<String> avisos = new ArrayList<>();
        try {
            Resultado r = enTransaccion(() -> trasladarEnTransaccion(ids, idCustodio, avisos));
            if (r != null) {
                actividadService.registrar(r.autor(), ActividadService.MOD_TRANSFERENCIA, ActividadService.ACC_MOVIMIENTO,
                        r.referencia(), "Envío a custodia de faltantes (" + r.actas() + "): " + r.cantidad()
                                + " bien(es) a " + r.referencia(), r.cantidad(), null);
                emitir(r.actas(), r.cantidad());
            }
        } catch (Exception e) {
            log.warn("[CUSTODIA] No se pudo trasladar a la custodia {}: {}", idCustodio, e.getMessage());
            anotar(ids, ESPERANDO_ALTA, "No se pudo encolar el traslado: " + e.getMessage() + ". Se reintenta solo.");
        }
        avisos.forEach(a -> log.info("[CUSTODIA] {}", a));
    }

    private record Resultado(Usuario autor, String referencia, String actas, int cantidad) {}

    private Resultado trasladarEnTransaccion(List<Long> ids, Long idCustodio, List<String> avisos) {
        Responsable custodio = responsableService.findByIdWithRelations(idCustodio);
        Oficina ofCustodia = custodio.getOficina();
        Predio predio = ofCustodia.getPredio();
        boolean cola = actualDbfWriterService.esModoCola();
        LocalDateTime ahora = LocalDateTime.now();

        List<TransferenciaService.ActivoConOrigen> acos = new ArrayList<>();
        List<HallazgoInventario> movidos = new ArrayList<>();
        java.util.Set<String> actas = new java.util.LinkedHashSet<>();
        ActaFaltante primera = null;

        for (HallazgoInventario h : hallazgoDao.bloquear(ids)) {
            // Se relee con el bloqueo: pudo anularse el acta o cambiar el paso mientras tanto.
            if (!ESPERANDO_ALTA.equals(h.getEstadoEnvio())) continue;
            Activo a = h.getActivo();
            String problema = problemaParaTrasladar(h, a, predio);
            if (problema != null) {
                h.setEstadoEnvio(ERROR);
                h.setMensajeEnvio(problema);
                avisos.add(a.getCodigo() + ": " + problema);
                continue;
            }
            acos.add(new TransferenciaService.ActivoConOrigen(a));
            movidos.add(h);
            if (h.getActa() != null) {
                actas.add(h.getActa().getNumero());
                if (primera == null) primera = h.getActa();
            }
        }
        if (acos.isEmpty()) return null;

        Usuario autor = primera != null && primera.getRegistroIdUsuario() != null
                ? usuarioDao.findById(primera.getRegistroIdUsuario()).orElse(null) : null;
        String usuario = primera != null && primera.getUsuarioEmision() != null ? primera.getUsuarioEmision() : "SISTEMA";
        LocalDate hoy = LocalDate.now();

        for (TransferenciaService.ActivoConOrigen ac : acos) {
            Activo a = ac.activo;
            a.setOficina(ofCustodia);
            a.setResponsable(custodio);
            a.setFecMod(hoy);
            a.setUsuMod(usuario);
            a.setFechaUlt(hoy);
            a.setUsuario(usuario);
            a.setApiEstado(Short.valueOf("3"));
            a.setModificacion(new Date());
            if (autor != null) a.setModificacionIdUsuario(autor.getIdUsuario());
            a.setSincVsiaf(cola ? Activo.SINC_EN_COLA : Activo.SINC_CONFIRMADO);
            a.setSincVsiafMensaje(null);
            a.setSincVsiafFecha(ahora);
        }

        String listaActas = String.join(", ", actas);
        transferenciaService.registrarTransferencia(acos, "INTERNA", ofCustodia, custodio,
                autor != null ? autor.getIdUsuario() : null, usuario,
                recortar("Acta de faltantes " + listaActas, 120),
                "Envío a custodia de faltantes", null);

        // Último paso: si la orden no se puede dejar, la excepción deshace todo lo anterior.
        actualDbfWriterService.actualizarLoteTransferencias(acos.stream().map(ac -> ac.activo).toList(),
                predio.getEntidad().getEntidadCodigo(), predio.getUnidad(), usuario);

        for (HallazgoInventario h : movidos) {
            h.setFechaEnvioCustodia(ahora);
            h.setUsuarioEnvioCustodia(usuario);
            h.setMensajeEnvio(null);
            if (cola) {
                h.setEstadoEnvio(ENVIADO);
            } else {
                // Sin worker (modo bytes) la escritura ya se hizo: queda en custodia.
                h.setEstadoEnvio(CONFIRMADO);
                h.setEstadoHallazgo(ControlActivosService.EN_CUSTODIA);
            }
        }
        return new Resultado(autor, OficinaGestionService.referencia(ofCustodia), listaActas, movidos.size());
    }

    /** Por qué este bien no puede trasladarse ahora; null si puede. */
    private String problemaParaTrasladar(HallazgoInventario h, Activo a, Predio predioCustodia) {
        if (!Activo.ESTADO_ACTIVO.equals(a.getEstado())) return "El bien ya no está vigente.";
        if (Boolean.TRUE.equals(a.getBloqueado())) return "El bien está bloqueado: desbloquéelo y reintente.";
        Responsable actual = a.getResponsable();
        if (actual == null || actual.getPersona() == null || h.getResponsable() == null
                || h.getResponsable().getPersona() == null
                || !Objects.equals(actual.getPersona().getIdPersona(), h.getResponsable().getPersona().getIdPersona())) {
            return "El bien cambió de responsable después de registrar el acta.";
        }
        if (actual.isEsCustodia() || (a.getOficina() != null && a.getOficina().isEsCustodia())) {
            return "El bien ya está en una oficina de faltantes.";
        }
        Long idPredioBien = (a.getOficina() != null && a.getOficina().getPredio() != null)
                ? a.getOficina().getPredio().getIdPredio() : null;
        if (!Objects.equals(idPredioBien, predioCustodia.getIdPredio())) {
            return "El bien está en otro predio que la custodia de su responsable.";
        }
        return null;
    }

    /** Reintento de un traslado que el VSIAF rechazó: el bien ya está en la custodia del SCIAF. */
    private void reencolarTraslado(List<Long> ids, Usuario autor) {
        try {
            enTransaccion(() -> {
                String usuario = autor != null ? autor.getUsuario() : "SISTEMA";
                LocalDateTime ahora = LocalDateTime.now();
                List<HallazgoInventario> hs = hallazgoDao.bloquear(ids);
                Map<Long, List<Activo>> porPredio = new LinkedHashMap<>();
                Map<Long, Predio> predios = new LinkedHashMap<>();
                for (HallazgoInventario h : hs) {
                    Activo a = h.getActivo();
                    Predio p = a.getOficina().getPredio();
                    predios.putIfAbsent(p.getIdPredio(), p);
                    porPredio.computeIfAbsent(p.getIdPredio(), k -> new ArrayList<>()).add(a);
                }
                porPredio.forEach((idPredio, activos) -> {
                    Predio p = predios.get(idPredio);
                    actualDbfWriterService.actualizarLoteTransferencias(activos,
                            p.getEntidad().getEntidadCodigo(), p.getUnidad(), usuario);
                });
                boolean cola = actualDbfWriterService.esModoCola();
                for (HallazgoInventario h : hs) {
                    h.setFechaEnvioCustodia(ahora);
                    h.setMensajeEnvio(null);
                    h.setEstadoEnvio(cola ? ENVIADO : CONFIRMADO);
                    if (!cola) h.setEstadoHallazgo(ControlActivosService.EN_CUSTODIA);
                }
                return null;
            });
        } catch (Exception e) {
            anotar(ids, ERROR, "No se pudo reenviar el traslado: " + e.getMessage());
        }
    }

    // ── Paso 3: confirmación del worker ─────────────────────────────────────

    /**
     * Mira la orden de ACTUAL de cada bien trasladado: si el worker la aplicó, el faltante
     * queda EN_CUSTODIA; si la rechazó, ERROR con el motivo. También revisa los ERROR de
     * traslado, porque el reintento automático de la cola puede haberlos aplicado después.
     */
    private void confirmarTraslados() {
        int confirmados = enTransaccion(() -> {
            int n = 0;
            for (HallazgoInventario h : hallazgoDao.conEnvioEn(List.of(ENVIADO, ERROR))) {
                if (h.getFechaEnvioCustodia() == null) continue;   // ERROR de antes de trasladar
                // Solo los que siguen esperando: uno ya resuelto (p. ej. apareció y se devolvió)
                // tiene órdenes de ACTUAL posteriores que NO son su traslado a la custodia.
                if (!ControlActivosService.ABIERTO.equals(h.getEstadoHallazgo())) continue;
                Optional<DbfColaOrden> orden = colaDao
                        .findFirstByIdActivoAndTablaAndFechaEncoladoGreaterThanEqualOrderByIdOrdenDesc(
                                h.getActivo().getIdActivo(), "ACTUAL", h.getFechaEnvioCustodia().minusSeconds(5));
                if (orden.isEmpty()) continue;
                String estado = orden.get().getEstado();
                if (DbfColaOrden.OK.equals(estado)) {
                    h.setEstadoEnvio(CONFIRMADO);
                    h.setEstadoHallazgo(ControlActivosService.EN_CUSTODIA);
                    h.setMensajeEnvio(null);
                    n++;
                } else if (DbfColaOrden.ERROR.equals(estado) && ENVIADO.equals(h.getEstadoEnvio())) {
                    h.setEstadoEnvio(ERROR);
                    h.setMensajeEnvio("El VSIAF rechazó el traslado: "
                            + (orden.get().getMensaje() != null ? orden.get().getMensaje() : "sin motivo"));
                }
            }
            return n;
        });
        if (confirmados > 0) {
            log.info("[CUSTODIA] {} faltante(s) confirmados en custodia", confirmados);
            emitir(null, confirmados);
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private boolean yaEstaEnCustodia(HallazgoInventario h) {
        Activo a = h.getActivo();
        return h.getResponsableCustodia() != null && a.getResponsable() != null
                && Objects.equals(a.getResponsable().getIdResponsable(), h.getResponsableCustodia().getIdResponsable());
    }

    /** Deja el paso y el motivo en los hallazgos que todavía estén en un paso pendiente. */
    private void anotar(Collection<Long> ids, String estadoEnvio, String mensaje) {
        try {
            enTransaccion(() -> {
                for (HallazgoInventario h : hallazgoDao.bloquear(ids)) {
                    if (CONFIRMADO.equals(h.getEstadoEnvio()) || h.getEstadoEnvio() == null) continue;
                    h.setEstadoEnvio(estadoEnvio);
                    h.setMensajeEnvio(recortar(mensaje, 2000));
                }
                return null;
            });
        } catch (Exception e) {
            log.error("[CUSTODIA] No se pudo anotar el estado de envío: {}", e.getMessage(), e);
        }
    }

    private void emitir(String actas, int cantidad) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actas", actas);
        payload.put("cantidad", cantidad);
        try {
            sse.broadcast("faltantes-custodia", payload);
            sse.enviarARoles(ROLES_AVISO, "faltantes-custodia", payload);
        } catch (Exception e) {
            log.warn("[CUSTODIA] No se pudo emitir el evento: {}", e.getMessage());
        }
    }

    private static String recortar(String s, int max) {
        return (s == null || s.length() <= max) ? s : s.substring(0, max);
    }

    private <T> T enTransaccion(Supplier<T> trabajo) {
        return new TransactionTemplate(txManager).execute(estado -> trabajo.get());
    }
}
