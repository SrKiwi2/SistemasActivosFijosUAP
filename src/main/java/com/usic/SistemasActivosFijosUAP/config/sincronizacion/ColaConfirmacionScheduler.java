package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.dao.IActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cierra el circuito con el worker VFPOLEDB: lee lo que el worker dejó resuelto y lo
 * vuelca a la base.
 * <p>
 * El SCIAF encola una orden como archivo en {@code _cola/} y hasta ahora daba el envío
 * por bueno ahí mismo. El worker, en cambio, siempre dejó el resultado escrito: mueve
 * cada orden a {@code _hechos/} si la aplicó, o a {@code _errores/} —junto a un
 * {@code .error.txt} con el motivo— si la rechazó. Faltaba quien leyera esa respuesta.
 * <p>
 * Con esto un activo pasa a tener sincronización comprobada
 * ({@link Activo#SINC_CONFIRMADO}) o un error concreto que mostrarle a quien lo
 * registró, en vez de un "enviado al VSIAF" emitido antes de que nadie escribiera nada.
 *
 * <h4>Por qué consulta archivo por archivo</h4>
 * Se podría listar {@code _hechos/} y cruzar contra la base, pero esa carpeta es
 * histórica y crece sin tope: listarla por CIFS cada 20 segundos se vuelve caro para
 * siempre. Preguntar por las órdenes que siguen pendientes cuesta proporcional a lo que
 * falta resolver, que en reposo es cero.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ColaConfirmacionScheduler {

    private final IDbfColaOrdenDao colaDao;
    private final IActivoDao activoDao;
    private final SseEmitterRegistry sseRegistry;

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    /** Dónde están _cola/_hechos/_errores; por omisión, junto a los DBF. */
    @Value("${legacy.dbf.cola.path:${legacy.dbf.path:/mnt/dbfwin}}")
    private String colaPath;

    @Value("${legacy.dbf.write.mode:bytes}")
    private String writeMode;

    /** Tope de órdenes por pasada, para no quedarse minutos golpeando el CIFS. */
    @Value("${sync.cola.confirmacion.lote:200}")
    private int lote;

    /**
     * Una orden que ya no está en ninguna de las tres carpetas y lleva más de estas
     * horas encolada se da por perdida: alguien limpió la carpeta o el archivo nunca
     * llegó a escribirse. Dejarla "pendiente para siempre" escondería el problema.
     */
    @Value("${sync.cola.confirmacion.horas-extravio:6}")
    private long horasExtravio;

    @Scheduled(fixedDelayString = "${sync.cola.confirmacion.interval.ms:20000}", initialDelay = 30000)
    @Transactional
    public void confirmarOrdenes() {
        if (!"cola".equalsIgnoreCase(writeMode)) return;   // en modo bytes no hay worker que responda

        List<DbfColaOrden> pendientes = colaDao.findByEstadoOrderByIdOrdenAsc(
                DbfColaOrden.ENCOLADA, PageRequest.of(0, Math.max(1, lote)));
        if (pendientes.isEmpty()) return;

        Path cola    = Path.of(colaPath, "_cola");
        Path hechos  = Path.of(colaPath, "_hechos");
        Path errores = Path.of(colaPath, "_errores");

        int ok = 0, fallidas = 0, extraviadas = 0;
        Set<Long> activosTocados = new LinkedHashSet<>();

        for (DbfColaOrden orden : pendientes) {
            try {
                if (Files.exists(hechos.resolve(orden.getArchivo()))) {
                    resolver(orden, DbfColaOrden.OK, null);
                    ok++;

                } else if (Files.exists(errores.resolve(orden.getArchivo()))) {
                    resolver(orden, DbfColaOrden.ERROR, leerMotivo(errores, orden.getArchivo()));
                    fallidas++;

                } else if (!Files.exists(cola.resolve(orden.getArchivo())) && esVieja(orden)) {
                    resolver(orden, DbfColaOrden.ERROR,
                            "La orden desapareció de la cola sin dejar resultado. "
                            + "Verificá el worker y volvé a enviar el cambio.");
                    extraviadas++;

                } else {
                    continue;   // sigue en _cola: el worker todavía no llegó a ella
                }

                if (orden.getIdActivo() != null) activosTocados.add(orden.getIdActivo());

            } catch (Exception e) {
                // Un problema al mirar el CIFS no debe frenar el resto del lote ni
                // marcar la orden: en la próxima pasada se vuelve a intentar.
                log.warn("[COLA] No se pudo verificar la orden {}: {}", orden.getArchivo(), e.getMessage());
            }
        }

        if (ok + fallidas + extraviadas == 0) return;

        colaDao.flush();
        actualizarActivos(activosTocados);

        log.info("[COLA] Confirmadas {} · con error {} · extraviadas {} (quedaban {} pendientes)",
                ok, fallidas, extraviadas, pendientes.size());

        try {
            sseRegistry.broadcast("cola-vsiaf", Map.of(
                    "confirmadas", ok, "errores", fallidas, "extraviadas", extraviadas,
                    "ts", System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("[COLA] No se pudo emitir el SSE cola-vsiaf: {}", e.getMessage());
        }
    }

    private void resolver(DbfColaOrden orden, String estado, String mensaje) {
        orden.setEstado(estado);
        orden.setMensaje(mensaje);
        orden.setFechaResuelto(LocalDateTime.now());
        colaDao.save(orden);
    }

    private boolean esVieja(DbfColaOrden orden) {
        return orden.getFechaEncolado() != null
            && orden.getFechaEncolado().isBefore(LocalDateTime.now().minusHours(horasExtravio));
    }

    /** El worker escribe el motivo en un {@code .error.txt} con el mismo nombre base. */
    private String leerMotivo(Path errores, String archivo) {
        String base = archivo.endsWith(".json")
                ? archivo.substring(0, archivo.length() - ".json".length())
                : archivo;
        Path txt = errores.resolve(base + ".error.txt");
        try {
            if (Files.exists(txt)) {
                String motivo = Files.readString(txt, StandardCharsets.UTF_8).trim();
                if (!motivo.isEmpty()) {
                    return motivo.length() > 2000 ? motivo.substring(0, 2000) : motivo;
                }
            }
        } catch (Exception e) {
            log.debug("[COLA] No se pudo leer {}: {}", txt, e.getMessage());
        }
        return "El worker rechazó la orden y no dejó el motivo por escrito.";
    }

    /**
     * Recalcula la marca de cada activo tocado a partir de <b>todas</b> sus órdenes, no
     * de la que se acaba de resolver.
     * <p>
     * Un activo puede tener el INSERT confirmado y un UPDATE posterior en error: aplicar
     * el resultado de la última orden resuelta lo dejaría en CONFIRMADO cuando en
     * realidad hoy está desincronizado. Lo que importa mostrar es el saldo.
     */
    private void actualizarActivos(Set<Long> ids) {
        if (ids.isEmpty()) return;

        Map<Long, long[]> resumen = new HashMap<>();   // idActivo → [conError, pendientes]
        for (Object[] fila : colaDao.resumenPorActivo(new ArrayList<>(ids))) {
            resumen.put(((Number) fila[0]).longValue(),
                    new long[] { ((Number) fila[1]).longValue(), ((Number) fila[2]).longValue() });
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (Long id : ids) {
            long[] r = resumen.get(id);
            if (r == null) continue;

            String estado;
            String mensaje = null;
            if (r[0] > 0) {
                estado = Activo.SINC_ERROR;
                mensaje = mensajeDeError(id);
            } else if (r[1] > 0) {
                estado = Activo.SINC_EN_COLA;
            } else {
                estado = Activo.SINC_CONFIRMADO;
            }
            activoDao.marcarSincronizacionVsiaf(id, estado, mensaje, ahora);
        }
    }

    /** Motivo del error más reciente del activo, que es el que hay que mostrar. */
    private String mensajeDeError(Long idActivo) {
        return colaDao.findByIdActivoOrderByIdOrdenDesc(idActivo).stream()
                .filter(o -> DbfColaOrden.ERROR.equals(o.getEstado()))
                .map(DbfColaOrden::getMensaje)
                .filter(m -> m != null && !m.isBlank())
                .findFirst()
                .orElse("El VSIAF rechazó el último cambio enviado.");
    }
}
