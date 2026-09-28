package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reintenta sola una modificación de activo que el VSIAF rechazó, en vez de dejar que
 * dos sistemas queden distintos hasta que alguien note el aviso y vuelva a guardar.
 * <p>
 * Alcance deliberadamente acotado a ACTUAL/UPDATE con {@code idActivo} conocido. Para
 * OFICINA, RESP y AUXILIAR la orden solo guarda un código de referencia ("CODOFIC=384"),
 * que no es único por sí solo —el mismo código existe en distintos predios—: reintentar
 * ahí arriesgaría escribir sobre el registro equivocado. Ese caso sigue resolviéndose a
 * mano, volviendo a guardar desde el módulo correspondiente.
 * <p>
 * Antes de reintentar, cada rechazo se relee desde la base, no desde el archivo que
 * falló: si el activo cambió en el medio (o alguien ya lo volvió a guardar a mano), lo
 * que se reenvía es el dato vigente, no una fotografía vieja.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColaVsiafReintentoScheduler {

    private final IDbfColaOrdenDao colaDao;
    private final IActivoDao activoDao;
    private final ActualDbfWriterService actualDbfWriterService;

    @Value("${legacy.dbf.write.mode:bytes}")
    private String writeMode;

    /** Tope de rechazos a revisar por pasada. */
    @Value("${sync.cola.reintento.lote:50}")
    private int lote;

    /** Reintentos por cadena antes de dejar el rechazo firme (ya avisado por separado). */
    @Value("${sync.cola.reintento.max-intentos:3}")
    private int maxIntentos;

    private static final String USUARIO_REINTENTO = "sistema (reintento automático)";

    @Scheduled(fixedDelayString = "${sync.cola.reintento.interval.ms:300000}",
               initialDelayString = "${sync.cola.reintento.initial.delay.ms:180000}")
    @Transactional
    public void reintentarActivosRechazados() {
        if (!"cola".equalsIgnoreCase(writeMode)) return;

        List<DbfColaOrden> rechazadas = colaDao.findByEstadoAndTablaOrderByIdOrdenAsc(
                DbfColaOrden.ERROR, "ACTUAL", PageRequest.of(0, Math.max(1, lote)));
        if (rechazadas.isEmpty()) return;

        int reintentadas = 0, agotadas = 0, superadas = 0;
        for (DbfColaOrden vieja : rechazadas) {
            if (vieja.getIdActivo() == null) continue;   // no debería pasar en ACTUAL, por las dudas
            try {
                String resultado = procesar(vieja);
                switch (resultado) {
                    case "REINTENTADA" -> reintentadas++;
                    case "AGOTADA"     -> agotadas++;
                    case "SUPERADA"    -> superadas++;
                    default -> { /* sin cambios: activo aún no elegible */ }
                }
            } catch (Exception e) {
                log.warn("[COLA-REINTENTO] No se pudo evaluar la orden {}: {}",
                        vieja.getIdOrden(), e.getMessage());
            }
        }

        if (reintentadas + agotadas + superadas > 0) {
            log.info("[COLA-REINTENTO] Reintentadas {} · agotadas {} · superadas por envío manual {}",
                    reintentadas, agotadas, superadas);
        }
    }

    /** @return REINTENTADA, AGOTADA, SUPERADA, o SIN_CAMBIOS. */
    private String procesar(DbfColaOrden vieja) {
        // Alguien ya volvió a guardar este activo (a mano, o un reintento previo): esta
        // orden vieja no representa el estado actual y no hay nada más que hacerle.
        if (colaDao.existsByIdActivoAndIdOrdenGreaterThan(vieja.getIdActivo(), vieja.getIdOrden())) {
            vieja.setEstado(DbfColaOrden.REINTENTADA);
            vieja.setMensaje("Superada por un envío posterior del mismo activo.");
            colaDao.save(vieja);
            return "SUPERADA";
        }

        int intentosPrevios = vieja.getIntentos() == null ? 0 : vieja.getIntentos();
        if (intentosPrevios >= maxIntentos) {
            return "SIN_CAMBIOS";   // se dejó de insistir; el rechazo queda firme y ya avisado
        }

        Activo a = activoDao.findById(vieja.getIdActivo()).orElse(null);
        if (a == null || !"ACTIVO".equalsIgnoreCase(a.getEstado())) {
            return "SIN_CAMBIOS";   // se dio de baja o ya no aplica: no insistir
        }
        if (a.getOficina() == null || a.getOficina().getPredio() == null
                || a.getOficina().getPredio().getEntidad() == null) {
            return "SIN_CAMBIOS";   // le siguen faltando datos: reintentar fallaría igual
        }

        String entidadCode = a.getOficina().getPredio().getEntidad().getEntidadCodigo();
        String unidadCode = a.getOficina().getPredio().getUnidad();

        vieja.setEstado(DbfColaOrden.REINTENTADA);
        vieja.setMensaje("Reintentado automáticamente (intento " + (intentosPrevios + 1)
                + " de " + maxIntentos + ").");
        colaDao.save(vieja);

        actualDbfWriterService.actualizarDesdeActivo(a.getCodigo(), a, entidadCode, unidadCode,
                USUARIO_REINTENTO);

        // La orden nueva la creó actualizarDesdeActivo() por dentro (vía DbfColaService);
        // se la localiza por ser la más reciente del activo y se le anota el contador.
        colaDao.findFirstByIdActivoOrderByIdOrdenDesc(a.getIdActivo()).ifPresent(nueva -> {
            nueva.setIntentos(intentosPrevios + 1);
            colaDao.save(nueva);
        });

        return "REINTENTADA";
    }
}
