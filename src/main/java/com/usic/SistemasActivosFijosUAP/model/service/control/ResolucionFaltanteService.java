package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHallazgoInventarioDao;
import com.usic.SistemasActivosFijosUAP.model.dto.control.CustodiaDTOs;
import com.usic.SistemasActivosFijosUAP.model.dto.control.ResolverHallazgoRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.HallazgoInventario;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.CustodiaFaltantesRepo;
import com.usic.SistemasActivosFijosUAP.model.service.TransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.OficinaGestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolución de un faltante, incluida la salida de la custodia.
 * <ul>
 *   <li><b>APARECIO</b>: si el bien ya está en la oficina de faltantes, vuelve a un
 *       responsable del mismo predio (por omisión, quien lo tenía) con una transferencia
 *       interna que viaja al VSIAF. Si todavía no había llegado a la custodia, se cancela
 *       el traslado y el bien se queda donde está.</li>
 *   <li><b>JUSTIFICADO</b> / <b>DERIVADO_BAJA</b>: exigen documento. El bien tiene que estar
 *       en la custodia y se queda ahí, "pendiente de baja", hasta que la cola del VSIAF
 *       soporte bajas (hoy no hay baja definitiva en el SCIAF).</li>
 * </ul>
 * Un faltante sin acta (abierto por un levantamiento, nunca registrado) se resuelve como
 * siempre, en {@link ControlActivosService#resolver}.
 * <p>
 * La salida de la custodia no pasa por {@code Activo.exigirNoBloqueado} a propósito: es el
 * único camino por el que un bien sale de la oficina de faltantes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResolucionFaltanteService {

    public static final String APARECIO      = "APARECIO";
    public static final String JUSTIFICADO   = "JUSTIFICADO";
    public static final String DERIVADO_BAJA = "DERIVADO_BAJA";
    private static final Set<String> TIPOS = Set.of(APARECIO, JUSTIFICADO, DERIVADO_BAJA);

    private static final int MAX_ACCION = 2000;
    private static final int MAX_DOCUMENTO = 120;

    private final IHallazgoInventarioDao hallazgoDao;
    private final IResponsableService responsableService;
    private final CustodiaFaltantesRepo repo;
    private final ControlActivosService controlActivosService;
    private final TransferenciaService transferenciaService;
    private final ActualDbfWriterService actualDbfWriterService;
    private final ActividadService actividadService;
    private final SseEmitterRegistry sse;
    private final PlatformTransactionManager txManager;

    /**
     * @param tipoResolucion      APARECIO | JUSTIFICADO | DERIVADO_BAJA
     * @param accionCorrectiva    qué se hizo o qué se constató (obligatorio)
     * @param documento           respaldo: obligatorio para JUSTIFICADO y DERIVADO_BAJA
     * @param idResponsableDestino a quién vuelve el bien que apareció (si está en custodia)
     */
    public record Solicitud(String tipoResolucion, String accionCorrectiva, String documento,
                            Long idResponsableDestino) {}

    /** Situación del faltante para armar el diálogo de resolución. */
    public record Situacion(Long idHallazgo, String codigo, String descripcion, String numeroActa,
                            boolean enCustodia, boolean trasladoEnCurso, boolean conActa,
                            List<String> tiposPermitidos, String aviso) {}

    // ── Lectura para el diálogo ─────────────────────────────────────────────

    public Situacion situacion(Long idHallazgo) {
        return enTransaccion(() -> {
            HallazgoInventario h = hallazgoDao.findById(idHallazgo)
                    .orElseThrow(() -> new ReglaNegocioException("El faltante no existe."));
            Activo a = h.getActivo();
            boolean conActa = h.getActa() != null;
            boolean enCustodia = a != null && a.enCustodia();
            boolean enCurso = EnvioCustodiaService.ENVIADO.equals(h.getEstadoEnvio());
            List<String> tipos;
            String aviso = null;
            if (!conActa) {
                tipos = List.of(APARECIO, JUSTIFICADO, DERIVADO_BAJA);
            } else if (enCurso) {
                tipos = List.of();
                aviso = "El traslado a la custodia se está aplicando en el VSIAF. Espere a que se confirme.";
            } else if (EnvioCustodiaService.ERROR.equals(h.getEstadoEnvio()) && h.getFechaEnvioCustodia() != null) {
                tipos = List.of(APARECIO);
                aviso = "El VSIAF rechazó el traslado a la custodia. Si el bien apareció, puede devolverlo; "
                        + "para justificarlo o derivarlo a baja, primero reintente el traslado (pestaña Actas).";
            } else if (enCustodia) {
                tipos = List.of(APARECIO, JUSTIFICADO, DERIVADO_BAJA);
            } else if (ControlActivosService.EN_CUSTODIA.equals(h.getEstadoHallazgo())) {
                // Figura en custodia pero el bien está en otra oficina (lo movieron en el VSIAF).
                tipos = List.of(APARECIO);
                aviso = "El bien ya no está en la oficina de faltantes: solo se puede cerrar como 'Apareció'.";
            } else {
                tipos = List.of(APARECIO);
                aviso = "El bien todavía no llegó a la custodia: si apareció, se cancela el traslado. "
                        + "Justificar o derivar a baja se hace cuando ya está en custodia.";
            }
            return new Situacion(h.getIdHallazgo(), a != null ? a.getCodigo() : h.getCodigoFisico(),
                    a != null ? a.getDescripcion() : h.getDescripcionFisica(),
                    conActa ? h.getActa().getNumero() : null, enCustodia, enCurso, conActa, tipos, aviso);
        });
    }

    /** Responsables del mismo predio a los que puede volver el bien; el original primero. */
    public List<CustodiaDTOs.Destino> destinos(Long idHallazgo, String texto) {
        return enTransaccion(() -> {
            HallazgoInventario h = hallazgoDao.findById(idHallazgo)
                    .orElseThrow(() -> new ReglaNegocioException("El faltante no existe."));
            Activo a = h.getActivo();
            if (a == null || a.getOficina() == null || a.getOficina().getPredio() == null) return List.of();
            Long original = h.getResponsable() != null ? h.getResponsable().getIdResponsable() : -1L;
            Long origen = h.getOficinaOrigen() != null ? h.getOficinaOrigen().getIdOficina() : null;
            return repo.destinos(a.getOficina().getPredio().getIdPredio(), original, origen, texto, 40);
        });
    }

    // ── Resolución ──────────────────────────────────────────────────────────

    public String resolver(Long idHallazgo, Solicitud s, Usuario autor) {
        String tipo = s == null || s.tipoResolucion() == null ? null : s.tipoResolucion().trim().toUpperCase();
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw new ReglaNegocioException("Indique cómo se resuelve: Apareció, Justificado o Derivado a baja.");
        }
        String accion = limpio(s.accionCorrectiva());
        if (accion == null) throw new ReglaNegocioException("Describa qué se hizo o qué se constató.");
        if (accion.length() > MAX_ACCION) {
            throw new ReglaNegocioException("La descripción admite hasta " + MAX_ACCION + " caracteres.");
        }
        String documento = limpio(s.documento());
        if (documento != null && documento.length() > MAX_DOCUMENTO) {
            throw new ReglaNegocioException("El documento admite hasta " + MAX_DOCUMENTO + " caracteres.");
        }
        String usuario = autor != null ? autor.getUsuario() : "SISTEMA";

        Hecho hecho = enTransaccion(() -> resolverEnTransaccion(idHallazgo, tipo, accion, documento,
                s.idResponsableDestino(), autor, usuario));

        actividadService.registrar(autor, ActividadService.MOD_ACTIVO, ActividadService.ACC_MODIFICACION,
                hecho.codigo(), "Resolvió el faltante " + hecho.codigo() + " (" + tipo + "): " + hecho.mensaje(),
                idHallazgo);
        emitir(hecho.codigo());
        return hecho.mensaje();
    }

    private record Hecho(String codigo, String mensaje) {}

    private Hecho resolverEnTransaccion(Long idHallazgo, String tipo, String accion, String documento,
                                       Long idDestino, Usuario autor, String usuario) {
        List<HallazgoInventario> bloqueados = hallazgoDao.bloquear(List.of(idHallazgo));
        if (bloqueados.isEmpty()) throw new ReglaNegocioException("El faltante no existe.");
        HallazgoInventario h = bloqueados.get(0);

        // Sin acta: el camino de siempre (no hay custodia de por medio).
        if (h.getActa() == null && h.getEstadoEnvio() == null) {
            String texto = documento != null ? "Doc.: " + documento + ". " + accion : accion;
            controlActivosService.resolver(idHallazgo, new ResolverHallazgoRequest(tipo, texto), usuario);
            return new Hecho(codigoDe(h), "Faltante resuelto.");
        }

        if (ControlActivosService.RESUELTO.equals(h.getEstadoHallazgo())) {
            throw new ReglaNegocioException("El faltante ya está resuelto.");
        }
        if (ActaFaltanteService.ANULADO.equals(h.getEstadoHallazgo())) {
            throw new ReglaNegocioException("El faltante se anuló junto con su acta.");
        }
        if (EnvioCustodiaService.ENVIADO.equals(h.getEstadoEnvio())) {
            throw new ReglaNegocioException("El traslado a la custodia se está aplicando en el VSIAF. "
                    + "Espere a que se confirme y vuelva a intentar.");
        }
        if (!ControlActivosService.TIPO_FALTANTE.equals(h.getTipoHallazgo())) {
            throw new ReglaNegocioException("Solo los faltantes se resuelven desde la custodia.");
        }

        Activo a = h.getActivo();
        boolean enCustodia = a != null && a.enCustodia();
        boolean noLlegoACustodia = !enCustodia && !ControlActivosService.EN_CUSTODIA.equals(h.getEstadoHallazgo());
        boolean trasladoConError = EnvioCustodiaService.ERROR.equals(h.getEstadoEnvio()) && h.getFechaEnvioCustodia() != null;
        String mensaje;

        if (!APARECIO.equals(tipo)) {
            if (trasladoConError) {
                // En el SCIAF está en custodia, en el VSIAF no: quedaría así para siempre.
                throw new ReglaNegocioException("El VSIAF rechazó el traslado de este bien a la custodia. Reintente "
                        + "el traslado desde la pestaña Actas antes de justificarlo o derivarlo a baja.");
            }
            if (!enCustodia) {
                throw new ReglaNegocioException("Justificar o derivar a baja se hace cuando el bien ya está en la "
                        + "oficina de faltantes. Si apareció, resuélvalo como 'Apareció'.");
            }
            if (documento == null) {
                throw new ReglaNegocioException("Indique el documento que respalda la resolución.");
            }
            mensaje = "Faltante resuelto (" + (JUSTIFICADO.equals(tipo) ? "justificado" : "derivado a baja")
                    + "). El bien se queda en la oficina de faltantes, pendiente de baja.";

        } else if (noLlegoACustodia) {
            // Apareció antes de llegar a la custodia: se corta el traslado y el bien no se mueve.
            h.setEstadoEnvio(null);
            h.setMensajeEnvio("Resuelto antes del traslado a la custodia.");
            mensaje = "Faltante resuelto. El traslado a la custodia se canceló: el bien sigue con su responsable.";

        } else if (!enCustodia) {
            // Figuraba en custodia, pero el bien ya estaba en otra oficina (movido en el VSIAF).
            mensaje = "Faltante resuelto. El bien ya estaba fuera de la custodia ("
                    + (a != null && a.getOficina() != null ? OficinaGestionService.referencia(a.getOficina()) : "—")
                    + "): no se movió.";

        } else {
            mensaje = devolver(h, a, idDestino, autor, usuario);
        }

        // Un paso de traslado que quedó pendiente o con error ya no corre: el caso se cerró.
        if (EnvioCustodiaService.ERROR.equals(h.getEstadoEnvio())
                || EnvioCustodiaService.ESPERANDO_ALTA.equals(h.getEstadoEnvio())) {
            h.setEstadoEnvio(null);
            h.setMensajeEnvio("Traslado cerrado al resolver el faltante.");
        }
        h.setEstadoHallazgo(ControlActivosService.RESUELTO);
        h.setTipoResolucion(tipo);
        h.setAccionCorrectiva(documento != null ? "Doc.: " + documento + ". " + accion : accion);
        h.setFechaResolucion(LocalDateTime.now());
        h.setUsuarioRevisor(usuario);
        if (h.getInventario() != null) controlActivosService.recalcularFaltantesDe(h.getInventario());

        log.info("[CUSTODIA] Faltante {} ({}) resuelto como {} por {}", h.getIdHallazgo(), codigoDe(h), tipo, usuario);
        return new Hecho(codigoDe(h), mensaje);
    }

    /**
     * Saca el bien de la custodia hacia un responsable del mismo predio: transferencia
     * interna con historial y UPDATE de ACTUAL. Si la orden no se puede dejar, la excepción
     * deshace todo (el faltante sigue en custodia).
     */
    private String devolver(HallazgoInventario h, Activo a, Long idDestino, Usuario autor, String usuario) {
        if (Boolean.TRUE.equals(a.getBloqueado())) {
            throw new ReglaNegocioException("El activo " + a.getCodigo() + " está bloqueado: desbloquéelo antes de devolverlo.");
        }
        // Un regularizado se imputa a la fila de custodia: ahí no puede volver, hay que elegir.
        Long id = idDestino != null ? idDestino
                : (h.getResponsable() != null && !h.getResponsable().isEsCustodia()
                        ? h.getResponsable().getIdResponsable() : null);
        if (id == null) throw new ReglaNegocioException("Elija a qué responsable vuelve el bien.");
        Responsable destino = responsableService.findByIdWithRelations(id);
        if (destino == null) throw new ReglaNegocioException("El responsable elegido no existe.");
        if (!"ACTIVO".equals(destino.getEstado())) {
            throw new ReglaNegocioException("El responsable elegido ya no está vigente: elija otro.");
        }
        Oficina ofDestino = destino.getOficina();
        String destinoInvalido = ReglasCustodia.motivoDestino(ofDestino, destino);
        if (destinoInvalido != null) throw new ReglaNegocioException(destinoInvalido);
        if (ofDestino == null || !"ACTIVO".equals(ofDestino.getEstado())) {
            throw new ReglaNegocioException("La oficina del responsable elegido no está vigente.");
        }
        Predio predio = a.getOficina().getPredio();
        if (ofDestino.getPredio() == null || !Objects.equals(ofDestino.getPredio().getIdPredio(), predio.getIdPredio())) {
            throw new ReglaNegocioException("El bien vuelve a un responsable del mismo predio (" + predio.getUnidad()
                    + "). Para llevarlo a otro predio, devuélvalo acá y después transfiéralo.");
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
        a.setSincVsiaf(cola ? Activo.SINC_EN_COLA : Activo.SINC_CONFIRMADO);
        a.setSincVsiafMensaje(null);
        a.setSincVsiafFecha(LocalDateTime.now());

        String acta = h.getActa() != null ? h.getActa().getNumero() : "";
        transferenciaService.registrarTransferencia(List.of(aco), "INTERNA", ofDestino, destino,
                autor != null ? autor.getIdUsuario() : null, usuario,
                ("Faltante resuelto — acta " + acta).trim(), "Devolución desde la custodia de faltantes", null);

        // Último paso: si la orden no se puede dejar (montaje caído), se revierte todo.
        actualDbfWriterService.actualizarLoteTransferencias(List.of(a),
                predio.getEntidad().getEntidadCodigo(), predio.getUnidad(), usuario);

        String nombre = destino.getPersona() != null ? destino.getPersona().getNombreCompleto() : destino.getCodigoFuncionario();
        return "Faltante resuelto. El bien volvió a " + nombre + " (" + OficinaGestionService.referencia(ofDestino) + ")"
                + (cola ? "; el VSIAF lo aplica en unos segundos." : ".");
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private static String codigoDe(HallazgoInventario h) {
        return h.getActivo() != null ? h.getActivo().getCodigo() : h.getCodigoFisico();
    }

    private static String limpio(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private void emitir(String codigo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("resuelto", codigo);
        try {
            sse.broadcast("faltantes-custodia", payload);
        } catch (Exception e) {
            log.warn("[CUSTODIA] No se pudo emitir el evento: {}", e.getMessage());
        }
    }

    private <T> T enTransaccion(Supplier<T> trabajo) {
        return new TransactionTemplate(txManager).execute(estado -> trabajo.get());
    }
}
