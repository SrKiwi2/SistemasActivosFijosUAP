package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.IService.INotificacionService;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;
import com.usic.SistemasActivosFijosUAP.model.entity.Notificacion;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisa solo cuando lo enviado al VSIAF no llega.
 * <p>
 * El circuito ya se cierra sin intervención: el SCIAF encola, el worker aplica y
 * {@link ColaConfirmacionScheduler} marca el resultado. Lo que faltaba era el aviso.
 * Mientras nadie mire la cola a propósito, un worker caído o una orden rechazada pasan
 * inadvertidos, y los dos sistemas se van separando en silencio: exactamente lo que hay
 * que evitar, porque el desfase se descubre semanas después y ya no se sabe qué versión
 * del dato es la buena.
 * <p>
 * Avisa por dos motivos distintos, y solo una vez por cada uno:
 * <ul>
 *   <li><b>Órdenes rechazadas</b>: el VSIAF no aceptó el cambio. Trae el motivo textual.</li>
 *   <li><b>Cola trabada</b>: hay órdenes esperando hace más de lo razonable, señal de que
 *       el worker de la VM no está corriendo.</li>
 * </ul>
 * El corte de repetición vive en memoria a propósito: al reiniciar el sistema conviene
 * que vuelva a avisar si el problema sigue en pie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColaVsiafAlertaScheduler {

    private final IDbfColaOrdenDao colaDao;
    private final INotificacionService notificacionService;
    private final SseEmitterRegistry sseRegistry;

    @Value("${legacy.dbf.write.mode:bytes}")
    private String writeMode;

    /** A partir de cuántos minutos de espera se considera que la cola está trabada. */
    @Value("${sync.cola.alerta.minutos-espera:10}")
    private long minutosEspera;

    /** Cada cuánto se repite el aviso de cola trabada mientras el problema siga. */
    @Value("${sync.cola.alerta.repetir-horas:2}")
    private long repetirHoras;

    private static final List<String> ROLES = List.of("SUPER USUARIO", "ADMINISTRADOR");
    private static final String URL_DESTINO = "/api/estado/cola-vsiaf";

    /** Última orden con error ya avisada: por debajo de esto no se vuelve a notificar. */
    private long ultimoErrorAvisado = 0L;

    /** Cuándo se avisó por última vez que la cola estaba trabada. */
    private LocalDateTime ultimoAvisoAtasco;

    /**
     * Al arrancar, se toma como ya avisado todo lo que falló antes.
     * <p>
     * Sin esto, el primer arranque después de desplegar avisaría de rechazos viejos —que
     * quizá ya se corrigieron a mano— y la alerta nacería desacreditada. Lo que importa
     * avisar es lo que falla de ahora en adelante; lo histórico se consulta en el tablero.
     */
    @PostConstruct
    void tomarPuntoDePartida() {
        if (!"cola".equalsIgnoreCase(writeMode)) return;
        try {
            colaDao.findFirstByEstadoOrderByIdOrdenDesc(DbfColaOrden.ERROR)
                   .ifPresent(o -> ultimoErrorAvisado = o.getIdOrden());
            log.info("[COLA-ALERTA] Avisos activos. Se ignoran los rechazos hasta la orden {}.",
                    ultimoErrorAvisado);
        } catch (Exception e) {
            log.warn("[COLA-ALERTA] No se pudo leer el último rechazo: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${sync.cola.alerta.interval.ms:300000}",
               initialDelayString = "${sync.cola.alerta.initial.delay.ms:120000}")
    @Transactional
    public void revisar() {
        if (!"cola".equalsIgnoreCase(writeMode)) return;

        try {
            avisarRechazadas();
            avisarAtasco();
        } catch (Exception e) {
            // Un fallo del aviso nunca debe tumbar la tarea programada.
            log.warn("[COLA-ALERTA] No se pudo revisar el estado de la cola: {}", e.getMessage());
        }
    }

    /** Órdenes que el VSIAF rechazó y todavía no se avisaron. */
    private void avisarRechazadas() {
        List<DbfColaOrden> nuevas = colaDao.findByEstadoAndIdOrdenGreaterThanOrderByIdOrdenAsc(
                DbfColaOrden.ERROR, ultimoErrorAvisado, PageRequest.of(0, 20));
        if (nuevas.isEmpty()) return;

        String detalle = nuevas.stream()
                .map(o -> o.getTabla() + " " + o.getOperacion() + " "
                        + (o.getReferencia() != null ? o.getReferencia() : o.getClave())
                        + ": " + recortar(o.getMensaje(), 160))
                .collect(Collectors.joining("\n"));

        String titulo = nuevas.size() == 1
                ? "El VSIAF rechazó un cambio"
                : "El VSIAF rechazó " + nuevas.size() + " cambios";

        notificar(titulo,
                detalle + "\n\nEsos datos quedaron distintos entre el SCIAF y el VSIAF. "
                        + "Volvé a guardar el registro para reenviarlo.",
                "cola-error-" + nuevas.get(nuevas.size() - 1).getIdOrden());

        ultimoErrorAvisado = nuevas.get(nuevas.size() - 1).getIdOrden();
        log.warn("[COLA-ALERTA] {} orden(es) rechazadas por el VSIAF, notificado a {}",
                nuevas.size(), ROLES);
    }

    /** Órdenes esperando hace demasiado: el worker no las está levantando. */
    private void avisarAtasco() {
        List<DbfColaOrden> pendientes = colaDao.findByEstadoOrderByIdOrdenAsc(
                DbfColaOrden.ENCOLADA, PageRequest.of(0, 1));
        if (pendientes.isEmpty()) {
            ultimoAvisoAtasco = null;   // la cola se destrabó: el próximo problema vuelve a avisar
            return;
        }

        LocalDateTime masVieja = pendientes.get(0).getFechaEncolado();
        long minutos = Duration.between(masVieja, LocalDateTime.now()).toMinutes();
        if (minutos < minutosEspera) return;

        if (ultimoAvisoAtasco != null
                && ultimoAvisoAtasco.isAfter(LocalDateTime.now().minusHours(repetirHoras))) {
            return;   // ya se avisó hace poco y el problema es el mismo
        }

        long cuantas = colaDao.countByEstado(DbfColaOrden.ENCOLADA);
        notificar("Los cambios no están llegando al VSIAF",
                cuantas + " cambio(s) esperando hace " + minutos + " minutos. "
                + "El worker de la VM del VSIAF no los está aplicando, así que lo que se "
                + "guarde ahora queda solo en el SCIAF. Hay que revisar la tarea Worker-Vsiaf.",
                "cola-atasco");

        ultimoAvisoAtasco = LocalDateTime.now();
        log.warn("[COLA-ALERTA] Cola trabada: {} órdenes, la más vieja hace {} min", cuantas, minutos);
    }

    /** Notificación persistente para los roles administrativos, más el empujón en vivo. */
    private void notificar(String titulo, String mensaje, String referencia) {
        try {
            notificacionService.crearParaRoles(ROLES, Notificacion.TipoNotificacion.SISTEMA,
                    titulo, mensaje, referencia, URL_DESTINO);
        } catch (Exception e) {
            log.warn("[COLA-ALERTA] No se pudo crear la notificación: {}", e.getMessage());
        }
        try {
            sseRegistry.broadcast("cola-vsiaf-alerta", Map.of(
                    "titulo", titulo, "mensaje", mensaje, "ts", System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("[COLA-ALERTA] No se pudo emitir el SSE: {}", e.getMessage());
        }
    }

    private String recortar(String s, int max) {
        if (s == null || s.isBlank()) return "sin motivo";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
