package com.usic.SistemasActivosFijosUAP.model.service;

import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Responde "¿qué pasó con lo que mandé al VSIAF?" sin tener que entrar a la VM Windows.
 * <p>
 * El SCIAF deja cada cambio como un JSON en {@code _cola/} y el worker VFPOLEDB lo
 * aplica al DBF. Cuando el worker no está corriendo, las órdenes se acumulan y desde el
 * sistema todo se ve normal: el cambio quedó guardado en la base y nadie avisa que el
 * VSIAF no lo recibió. Este diagnóstico junta las dos puntas —los archivos de las
 * carpetas y la tabla {@code dbf_cola_orden}— para que se vea de un vistazo si el worker
 * está trabajando, hace cuánto que no toca nada y qué órdenes están esperando.
 * <p>
 * La lectura del CIFS va en un hilo aparte con timeout: un montaje colgado tarda minutos
 * en fallar y no debe congelar la petición del navegador.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ColaVsiafDiagnosticoService {

    private final IDbfColaOrdenDao colaDao;

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfPath;

    /** Dónde están _cola/_hechos/_errores; por omisión, junto a los DBF. */
    @Value("${legacy.dbf.cola.path:${legacy.dbf.path:/mnt/dbfwin}}")
    private String colaPath;

    @Value("${legacy.dbf.write.mode:bytes}")
    private String writeMode;

    /**
     * Tiempo máximo que esperamos al CIFS antes de darlo por colgado.
     * <p>
     * Más holgado que el del monitor del topbar: acá se listan carpetas que pueden tener
     * miles de archivos sobre un montaje con {@code cache=none}, donde cada operación es
     * un viaje a la red.
     */
    @Value("${sync.cola.diagnostico.timeout.ms:8000}")
    private long timeoutMs;

    /**
     * Sin actividad del worker por más de estos minutos, teniendo órdenes esperando, se
     * lo reporta como detenido: en marcha procesa la cola cada pocos segundos.
     */
    @Value("${sync.cola.worker.minutos-inactividad:10}")
    private long minutosInactividad;

    /** Tope de archivos a contar por carpeta: el número exacto de un histórico no aporta. */
    private static final int TOPE_CONTEO = 2000;

    /** Cuánto del final de worker.log se lee. */
    private static final int TOPE_LOG_BYTES = 8192;

    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "diagnostico-cola-vsiaf");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void detener() {
        ejecutor.shutdownNow();
    }

    /** Foto completa: carpetas del worker + lo anotado en la base. */
    @Transactional(readOnly = true)
    public Map<String, Object> diagnostico() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("modoEscritura", writeMode);
        r.put("rutaDbf", dbfPath);
        r.put("rutaCola", colaPath);

        Map<String, Object> carpetas = leerCarpetasConTimeout();
        r.put("carpetas", carpetas);
        r.put("base", resumenBase());
        r.put("worker", estadoWorker(carpetas));
        return r;
    }

    // =========================================================================
    //  Lado archivos (lo que ve el worker)
    // =========================================================================

    private Map<String, Object> leerCarpetasConTimeout() {
        Future<Map<String, Object>> tarea = ejecutor.submit(this::leerCarpetas);
        try {
            return tarea.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            tarea.cancel(true);
            return Map.of("accesible", false,
                    "error", "No se pudo leer " + colaPath + " en " + timeoutMs
                           + " ms. El montaje del VSIAF puede estar caído o colgado.");
        }
    }

    private Map<String, Object> leerCarpetas() {
        Map<String, Object> m = new LinkedHashMap<>();
        Path base = Path.of(colaPath);
        if (!Files.isDirectory(base)) {
            m.put("accesible", false);
            m.put("error", "La carpeta " + colaPath + " no existe o no es accesible desde el servidor.");
            return m;
        }
        m.put("accesible", true);
        m.put("cola", contar(base.resolve("_cola")));
        m.put("hechos", contar(base.resolve("_hechos")));
        m.put("errores", contar(base.resolve("_errores")));
        m.put("log", leerLog(base.resolve("worker.log")));
        return m;
    }

    /**
     * Cuántos .json hay en la carpeta y cuándo se tocó por última vez.
     * <p>
     * La fecha sale del directorio, no de los archivos: sobre CIFS con {@code cache=none}
     * preguntar la fecha de cada archivo es un viaje a la red por archivo, y en {@code
     * _hechos}, que crece sin tope, eso agotaba el timeout y hacía que el diagnóstico
     * informara el montaje como caído estando sano. El directorio cambia de fecha cuando
     * el worker mueve algo adentro, que es justo lo que queremos saber.
     */
    private Map<String, Object> contar(Path dir) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) {
            m.put("existe", false);
            m.put("archivos", 0);
            return m;
        }
        m.put("existe", true);
        m.put("ultimoCambio", fechaDe(dir));
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.json")) {
            int n = 0;
            for (Path ignorado : ds) {
                if (++n >= TOPE_CONTEO) break;   // no hace falta el número exacto de un histórico
            }
            m.put("archivos", n);
            m.put("truncado", n >= TOPE_CONTEO);
        } catch (Exception e) {
            m.put("archivos", -1);
            m.put("error", e.getMessage());
        }
        return m;
    }

    /** Últimas líneas de worker.log: es donde el worker cuenta qué aplicó y qué rechazó. */
    private Map<String, Object> leerLog(Path log) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (!Files.isRegularFile(log)) {
            m.put("existe", false);
            return m;
        }
        m.put("existe", true);
        m.put("ultimaEscritura", fechaDe(log));
        try {
            // Solo el final del archivo: worker.log crece sin tope y leerlo entero por
            // CIFS es caro y no aporta nada, lo último es lo que interesa.
            m.put("ultimasLineas", ultimasLineas(log));
        } catch (Exception e) {
            m.put("error", "No se pudo leer worker.log: " + e.getMessage());
        }
        return m;
    }

    /** Últimas líneas del log, leyendo a lo sumo los últimos {@value #TOPE_LOG_BYTES} bytes. */
    private List<String> ultimasLineas(Path log) throws Exception {
        long tam = Files.size(log);
        long desde = Math.max(0, tam - TOPE_LOG_BYTES);
        byte[] buf = new byte[(int) Math.min(tam, TOPE_LOG_BYTES)];
        try (SeekableByteChannel ch = Files.newByteChannel(log)) {
            ch.position(desde);
            ByteBuffer bb = ByteBuffer.wrap(buf);
            while (bb.hasRemaining() && ch.read(bb) > 0) { /* leer hasta llenar */ }
        }
        String texto = new String(buf, StandardCharsets.UTF_8);
        List<String> lineas = new ArrayList<>(List.of(texto.split("\r?\n")));
        if (desde > 0 && !lineas.isEmpty()) lineas.remove(0);   // la primera quedó cortada
        return lineas.subList(Math.max(0, lineas.size() - 15), lineas.size());
    }

    private Instant mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private LocalDateTime fechaDe(Path p) {
        if (p == null) return null;
        return LocalDateTime.ofInstant(mtime(p), ZoneId.systemDefault());
    }

    // =========================================================================
    //  Lado base (lo que el SCIAF cree haber mandado)
    // =========================================================================

    private Map<String, Object> resumenBase() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("encoladas", colaDao.countByEstado(DbfColaOrden.ENCOLADA));
        m.put("confirmadas", colaDao.countByEstado(DbfColaOrden.OK));
        m.put("conError", colaDao.countByEstado(DbfColaOrden.ERROR));

        List<DbfColaOrden> pendientes = colaDao.findByEstadoOrderByIdOrdenAsc(
                DbfColaOrden.ENCOLADA, org.springframework.data.domain.PageRequest.of(0, 20));
        m.put("pendientes", pendientes.stream().map(this::resumir).toList());
        if (!pendientes.isEmpty()) {
            LocalDateTime masVieja = pendientes.get(0).getFechaEncolado();
            m.put("esperaMasLarga", masVieja);
            m.put("esperaMasLargaMinutos", Duration.between(masVieja, LocalDateTime.now()).toMinutes());
        }
        return m;
    }

    private Map<String, Object> resumir(DbfColaOrden o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("archivo", o.getArchivo());
        m.put("tabla", o.getTabla());
        m.put("operacion", o.getOperacion());
        m.put("clave", o.getClave());
        m.put("referencia", o.getReferencia());
        m.put("usuario", o.getUsuario());
        m.put("estado", o.getEstado());
        m.put("mensaje", o.getMensaje());
        m.put("fechaEncolado", o.getFechaEncolado());
        return m;
    }

    // =========================================================================
    //  Veredicto
    // =========================================================================

    /**
     * Traduce las dos vistas a una frase accionable. Lo que decide es la actividad
     * reciente del worker frente a lo que sigue esperando, no que las carpetas existan.
     */
    private Map<String, Object> estadoWorker(Map<String, Object> carpetas) {
        Map<String, Object> m = new LinkedHashMap<>();

        if (!"cola".equalsIgnoreCase(writeMode)) {
            m.put("estado", "NO_APLICA");
            m.put("detalle", "El sistema escribe los DBF directamente (modo " + writeMode
                           + "), no hay worker en el medio.");
            return m;
        }
        if (Boolean.FALSE.equals(carpetas.get("accesible"))) {
            m.put("estado", "SIN_ACCESO");
            m.put("detalle", String.valueOf(carpetas.get("error")));
            return m;
        }

        long enCola = colaDao.countByEstado(DbfColaOrden.ENCOLADA);
        LocalDateTime ultima = ultimaActividad(carpetas);
        m.put("ultimaActividad", ultima);

        // Worker viejo: aplica el SQL y no mueve el archivo, así que la orden queda en
        // _cola y se vuelve a ejecutar cada pocos segundos. Se reconoce porque faltan las
        // carpetas de resultado, que el worker actual crea al arrancar.
        if (enCola > 0 && !carpetaExiste(carpetas, "hechos")) {
            m.put("estado", "SIN_CONSTANCIA");
            m.put("detalle", "Falta la carpeta _hechos: el worker de la VM no está dejando el "
                    + "resultado de las órdenes. Puede estar aplicando los cambios y reprocesando "
                    + "las mismas " + enCola + " órdenes en bucle. Hay que actualizar "
                    + "Worker-Vsiaf.ps1 en la VM del VSIAF con la versión de tools/ y reiniciar "
                    + "la tarea programada.");
            return m;
        }

        if (enCola == 0) {
            m.put("estado", "AL_DIA");
            m.put("detalle", "No hay órdenes esperando: todo lo enviado ya fue resuelto.");
            return m;
        }

        long minutosQuieto = ultima == null
                ? Long.MAX_VALUE
                : Duration.between(ultima, LocalDateTime.now()).toMinutes();

        if (minutosQuieto > minutosInactividad) {
            m.put("estado", "DETENIDO");
            m.put("detalle", enCola + " orden(es) esperando y el worker no registra actividad"
                    + (ultima == null ? " nunca" : " desde hace " + minutosQuieto + " min")
                    + ". Hay que revisar la tarea programada Worker-Vsiaf en la VM del VSIAF: "
                    + "hasta que corra, los cambios no llegan al DBF.");
        } else {
            m.put("estado", "TRABAJANDO");
            m.put("detalle", enCola + " orden(es) en cola; el worker está procesando.");
        }
        return m;
    }

    private boolean carpetaExiste(Map<String, Object> carpetas, String clave) {
        Object c = carpetas.get(clave);
        return c instanceof Map<?, ?> cm && Boolean.TRUE.equals(cm.get("existe"));
    }

    /** Lo más reciente que hizo el worker: su log, o lo último que movió a _hechos / _errores. */
    @SuppressWarnings("unchecked")
    private LocalDateTime ultimaActividad(Map<String, Object> carpetas) {
        LocalDateTime max = null;
        Object log = carpetas.get("log");
        if (log instanceof Map<?, ?> lm) {
            max = mayor(max, (LocalDateTime) ((Map<String, Object>) lm).get("ultimaEscritura"));
        }
        for (String clave : List.of("hechos", "errores")) {
            Object c = carpetas.get(clave);
            if (c instanceof Map<?, ?> cm) {
                max = mayor(max, (LocalDateTime) ((Map<String, Object>) cm).get("ultimoCambio"));
            }
        }
        return max;
    }

    private LocalDateTime mayor(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }
}
