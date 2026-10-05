package com.usic.SistemasActivosFijosUAP.componet;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ¿Está el VSIAF al alcance de ESTE servidor? Lo preguntan las tareas programadas que leen
 * o escriben sus DBF (detector de cambios, sync de respaldo, cola del worker, custodias)
 * antes de trabajar.
 *
 * <p><b>Por qué hace falta:</b> el SCIAF se levanta también en las laptops de desarrollo,
 * conectado a la MISMA base de producción pero sin los montajes del VSIAF. Sin esta pausa,
 * esas tareas corrían igual: gastaban CPU y conexiones leyendo archivos que no existen, y
 * algunas podían tocar datos de producción (p. ej. dar por "extraviada" una orden de la cola
 * porque en la laptop no está la carpeta).
 *
 * <p>Modo ({@code sciaf.vsiaf.modo}):
 * <ul>
 *   <li>{@code auto} (por defecto): disponible si el monitor de conexiones ve el montaje
 *       (no CAÍDO). Se pausa solo y se reanuda solo cuando el montaje vuelve.</li>
 *   <li>{@code desactivado}: nunca (para desarrollo, aunque haya un montaje de prueba).</li>
 *   <li>{@code activo}: siempre (no consulta el monitor).</li>
 * </ul>
 * Los cambios de estado se registran UNA vez en el log, no en cada pasada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VsiafDisponibilidad {

    private final MonitorConexionesService monitor;

    @Value("${sciaf.vsiaf.modo:auto}")
    private String modo;

    /** Último estado avisado por recurso, para registrar solo los cambios. */
    private final Map<String, Boolean> avisado = new ConcurrentHashMap<>();

    /** DBF maestros del VSIAF (ACTUAL, RESP, OFICINA…) y la carpeta de la cola del worker. */
    public boolean dbf(String tarea) {
        return disponible(MonitorConexionesService.CLAVE_DBF, "DBF del VSIAF", tarea);
    }

    /** Carpeta de solicitudes de transferencia del VSIAF. */
    public boolean transferencias(String tarea) {
        return disponible(MonitorConexionesService.CLAVE_TRANSF, "transferencias del VSIAF", tarea);
    }

    /** Para mostrar en pantallas ("Sincronizar" sin VSIAF). */
    public String motivoDbf() {
        if (desactivado()) return "La conexión con el VSIAF está desactivada en este servidor (sciaf.vsiaf.modo=desactivado).";
        return "Este servidor no ve los DBF del VSIAF: " + monitor.detalle(MonitorConexionesService.CLAVE_DBF);
    }

    private boolean desactivado() {
        return "desactivado".equals(modo == null ? "" : modo.trim().toLowerCase(Locale.ROOT));
    }

    private boolean disponible(String clave, String recurso, String tarea) {
        String m = modo == null ? "auto" : modo.trim().toLowerCase(Locale.ROOT);
        boolean ok;
        if ("activo".equals(m)) {
            ok = true;
        } else if ("desactivado".equals(m)) {
            ok = false;
        } else {
            ok = monitor.disponible(clave);
        }
        Boolean antes = avisado.put(clave, ok);
        if (antes == null || antes != ok) {
            if (ok) {
                log.info("[VSIAF] {} disponible: se reanudan las tareas programadas que lo usan.", recurso);
            } else {
                log.warn("[VSIAF] {} NO disponible ({}): se pausan las tareas programadas que lo usan "
                        + "(primera en notarlo: {}). Se reanudan solas cuando vuelva.",
                        recurso, "desactivado".equals(m) ? "sciaf.vsiaf.modo=desactivado" : monitor.detalle(clave), tarea);
            }
        }
        return ok;
    }
}
