package com.usic.SistemasActivosFijosUAP.componet;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.EstadoConexionDto;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Vigila las conexiones de las que depende la interoperabilidad:
 * los dos montajes CIFS del VSIAF y la base de datos.
 *
 * <p><b>Por que no basta con {@code Files.exists()}:</b> si el montaje CIFS se
 * cae, {@code /mnt/dbfwin} <em>sigue existiendo</em> como carpeta local vacia y
 * cualquier chequeo ingenuo daria "OK" mientras el sistema lee cero registros.
 * Por eso la sonda contrasta contra {@code /proc/mounts}: ahi se ve si el punto
 * de montaje esta realmente respaldado por un share remoto.</p>
 *
 * <p><b>Por que cada sonda corre en su propio hilo con timeout:</b> un montaje
 * CIFS colgado (VM Windows apagada, red cortada) bloquea las llamadas de disco
 * durante minutos. Si sondearamos en el hilo del scheduler, el monitor -y el
 * resto de las tareas programadas- se congelarian justamente cuando mas falta
 * hace el aviso.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorConexionesService {

    private final SseEmitterRegistry sseRegistry;
    private final DataSource dataSource;

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String rutaDbf;

    @Value("${legacy.dbf.transferencias.path:/mnt/vsiaf_transferencias}")
    private String rutaTransferencias;

    @Value("${spring.datasource.url:}")
    private String jdbcUrl;

    /** Tiempo maximo que esperamos a una sonda antes de darla por colgada. */
    @Value("${monitor.conexiones.timeout.ms:4000}")
    private long timeoutMs;

    public static final String CLAVE_DBF    = "dbfwin";
    public static final String CLAVE_TRANSF = "transferencias";
    public static final String CLAVE_BD     = "postgres";

    /** Solo estos roles reciben el push SSE y ven el indicador. */
    public static final List<String> ROLES_MONITOR =
        List.of("ADMINISTRADOR", "SUPER USUARIO");

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    /** Ultimo estado conocido de cada conexion (lo que sirve el endpoint). */
    private final Map<String, EstadoConexionDto> ultimoEstado = new ConcurrentHashMap<>();

    /** Un ejecutor por sonda: aisla un montaje colgado del resto. */
    private final Map<String, ExecutorService> ejecutores = new ConcurrentHashMap<>();

    /** Sonda en vuelo por clave: si la anterior sigue colgada, no encolamos otra. */
    private final Map<String, Future<EstadoConexionDto>> enVuelo = new ConcurrentHashMap<>();

    // =========================================================================
    //  CICLO DE VIDA
    // =========================================================================

    @PostConstruct
    public void iniciar() {
        for (String clave : List.of(CLAVE_DBF, CLAVE_TRANSF, CLAVE_BD)) {
            ejecutores.put(clave, Executors.newSingleThreadExecutor(hiloDaemon("monitor-" + clave)));
        }
        log.info("MonitorConexionesService iniciado - vigilando {} | {} | BD",
                 rutaDbf, rutaTransferencias);
    }

    @PreDestroy
    public void detener() {
        ejecutores.values().forEach(ExecutorService::shutdownNow);
    }

    private ThreadFactory hiloDaemon(String nombre) {
        return r -> {
            Thread t = new Thread(r, nombre);
            t.setDaemon(true);   // un hilo colgado en CIFS no debe impedir el apagado
            return t;
        };
    }

    // =========================================================================
    //  SONDEO PROGRAMADO
    // =========================================================================

    @Scheduled(fixedDelayString   = "${monitor.conexiones.interval.ms:30000}",
               initialDelayString = "${monitor.conexiones.initial.delay.ms:25000}")
    public void verificarProgramado() {
        verificarTodo(false);
    }

    /** Verificacion bajo demanda (boton "Verificar ahora" del topbar). */
    public List<EstadoConexionDto> verificarAhora() {
        return verificarTodo(true);
    }

    /** Lo ultimo que sabemos, sin tocar el disco: respuesta instantanea. */
    public List<EstadoConexionDto> estadoActual() {
        if (ultimoEstado.isEmpty()) return verificarTodo(false);
        return List.of(CLAVE_DBF, CLAVE_TRANSF, CLAVE_BD).stream()
                   .map(ultimoEstado::get)
                   .filter(e -> e != null)
                   .toList();
    }

    /** Resumen para el color del icono: el peor estado manda. */
    public String estadoGlobal() {
        List<EstadoConexionDto> estados = estadoActual();
        if (estados.stream().anyMatch(e -> EstadoConexionDto.CAIDO.equals(e.estado())))
            return EstadoConexionDto.CAIDO;
        if (estados.stream().anyMatch(e -> EstadoConexionDto.DEGRADADO.equals(e.estado())))
            return EstadoConexionDto.DEGRADADO;
        return EstadoConexionDto.OK;
    }

    private List<EstadoConexionDto> verificarTodo(boolean forzado) {
        List<EstadoConexionDto> resultado = new ArrayList<>(3);

        resultado.add(registrar(sondearMount(
            CLAVE_DBF, "VSIAF - DBF maestros", rutaDbf, "ACTUAL.DBF")));

        resultado.add(registrar(sondearMount(
            CLAVE_TRANSF, "VSIAF - Transferencias", rutaTransferencias, "sol_transferencias.DBF")));

        resultado.add(registrar(sondearBaseDatos()));

        if (forzado) log.debug("Verificacion de conexiones bajo demanda: {}", resultado);
        return resultado;
    }

    /**
     * Guarda el estado y, si cambio respecto del anterior, avisa a los
     * administradores conectados por SSE (y deja rastro en el log).
     */
    private EstadoConexionDto registrar(EstadoConexionDto nuevo) {
        EstadoConexionDto previo = ultimoEstado.put(nuevo.clave(), nuevo);

        if (nuevo.huboNovedad(previo)) {
            if (EstadoConexionDto.OK.equals(nuevo.estado())) {
                log.info("Conexion restablecida: {} - {}", nuevo.nombre(), nuevo.detalle());
            } else {
                log.warn("Conexion {}: {} - {}", nuevo.estado(), nuevo.nombre(), nuevo.detalle());
            }
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("conexion", nuevo);
                payload.put("estadoGlobal", estadoGlobal());
                payload.put("estadoPrevio", previo == null ? null : previo.estado());
                sseRegistry.enviarARoles(ROLES_MONITOR, "estado-conexiones", payload);
            } catch (Exception e) {
                log.debug("No se pudo emitir el evento SSE de estado: {}", e.getMessage());
            }
        }
        return nuevo;
    }

    // =========================================================================
    //  SONDA DE MONTAJE CIFS
    // =========================================================================

    private EstadoConexionDto sondearMount(String clave, String nombre, String ruta, String centinela) {

        // /proc/mounts es procfs: se lee al instante aunque el share este colgado.
        InfoMontaje info = leerInfoMontaje(ruta);

        return sondearConTimeout(clave, nombre, "CIFS", ruta, info,
            () -> {
                long t0 = System.nanoTime();
                Path base = Path.of(ruta);

                if (!Files.isDirectory(base)) {
                    return caido(clave, nombre, "CIFS", ruta, info,
                        "La carpeta no existe en el servidor. Falta crear el punto de montaje.",
                        "ruta inexistente: " + ruta, ms(t0));
                }

                // El caso traicionero: carpeta presente, montaje ausente.
                if (Boolean.FALSE.equals(info.montado())) {
                    return caido(clave, nombre, "CIFS", ruta, info,
                        "La carpeta existe pero NO hay ningun montaje activo: el sistema "
                        + "no esta leyendo los DBF del VSIAF. Hay que volver a ejecutar el mount.",
                        "sin entrada en /proc/mounts para " + ruta, ms(t0));
                }

                int  archivos = 0;
                Path rutaCentinela = null;
                try (Stream<Path> hijos = Files.list(base)) {
                    for (Path p : hijos.toList()) {
                        String n = p.getFileName().toString();
                        if (n.toLowerCase(Locale.ROOT).endsWith(".dbf")) archivos++;
                        if (n.equalsIgnoreCase(centinela)) rutaCentinela = p;
                    }
                }

                if (archivos == 0) {
                    return new EstadoConexionDto(clave, nombre, "CIFS",
                        EstadoConexionDto.DEGRADADO,
                        "Montaje activo pero la carpeta esta vacia: no se ve ningun archivo .DBF.",
                        info.origen(), ruta, info.montado(), 0,
                        Files.isWritable(base), ms(t0), null, ahora(),
                        "0 archivos .DBF en " + ruta);
                }

                if (rutaCentinela == null) {
                    return new EstadoConexionDto(clave, nombre, "CIFS",
                        EstadoConexionDto.DEGRADADO,
                        "Se ven " + archivos + " archivos .DBF pero falta " + centinela
                        + ", que es el que consulta el sistema.",
                        info.origen(), ruta, info.montado(), archivos,
                        Files.isWritable(base), ms(t0), null, ahora(),
                        "no se encontro " + centinela);
                }

                // Lectura real de bytes: confirma que hay dialogo con el archivo,
                // no solo que el nombre aparece en el listado.
                int primerByte;
                try (InputStream in = Files.newInputStream(rutaCentinela)) {
                    primerByte = in.read();
                }

                boolean escribible   = Files.isWritable(base);
                String  ultimoCambio = FMT.format(LocalDateTime.ofInstant(
                    Files.getLastModifiedTime(rutaCentinela).toInstant(), ZoneId.systemDefault()));
                long    tamanioKb    = Files.size(rutaCentinela) / 1024;

                if (primerByte < 0) {
                    return new EstadoConexionDto(clave, nombre, "CIFS",
                        EstadoConexionDto.DEGRADADO,
                        centinela + " esta vacio (0 bytes).",
                        info.origen(), ruta, info.montado(), archivos, escribible,
                        ms(t0), ultimoCambio, ahora(), centinela + " sin contenido");
                }

                String detalle = archivos + " archivos .DBF | " + centinela
                               + " " + tamanioKb + " KB, leido correctamente"
                               + (escribible ? "" : " | SOLO LECTURA");

                return new EstadoConexionDto(clave, nombre, "CIFS",
                    escribible ? EstadoConexionDto.OK : EstadoConexionDto.DEGRADADO,
                    detalle, info.origen(), ruta, info.montado(), archivos, escribible,
                    ms(t0), ultimoCambio, ahora(),
                    escribible ? null : "montado sin permiso de escritura (falta uid/gid en el mount)");
            });
    }

    // =========================================================================
    //  SONDA DE BASE DE DATOS
    // =========================================================================

    private EstadoConexionDto sondearBaseDatos() {
        String       destino = resumirJdbc(jdbcUrl);
        InfoMontaje  info    = new InfoMontaje(null, destino, null);

        return sondearConTimeout(CLAVE_BD, "Base de datos PostgreSQL", "PostgreSQL",
            null, info,
            () -> {
                long t0 = System.nanoTime();
                try (Connection cn = dataSource.getConnection()) {
                    boolean viva = cn.isValid((int) Math.max(1, timeoutMs / 1000));
                    String  bd   = cn.getCatalog();
                    if (!viva) {
                        return caido(CLAVE_BD, "Base de datos PostgreSQL", "PostgreSQL",
                            null, info,
                            "La conexion existe pero no responde a la validacion.",
                            "isValid() = false", ms(t0));
                    }
                    return new EstadoConexionDto(CLAVE_BD, "Base de datos PostgreSQL", "PostgreSQL",
                        EstadoConexionDto.OK,
                        "Conectado a " + bd + " | respuesta en " + ms(t0) + " ms",
                        destino, null, null, 0, true, ms(t0), null, ahora(), null);
                }
            });
    }

    // =========================================================================
    //  INFRAESTRUCTURA DE SONDEO (timeout + aislamiento)
    // =========================================================================

    private EstadoConexionDto sondearConTimeout(String clave, String nombre, String tipo,
                                                String ruta, InfoMontaje info,
                                                Callable<EstadoConexionDto> sonda) {

        Future<EstadoConexionDto> anterior = enVuelo.get(clave);
        if (anterior != null && !anterior.isDone()) {
            // La sonda previa sigue bloqueada: el recurso esta colgado, no insistimos.
            return caido(clave, nombre, tipo, ruta, info,
                "Sin respuesta: el recurso esta colgado (la verificacion anterior "
                + "todavia no retorna). Suele pasar si la VM se apago con el montaje abierto.",
                "sonda previa bloqueada > " + timeoutMs + " ms", timeoutMs);
        }

        Future<EstadoConexionDto> futuro = ejecutores.get(clave).submit(sonda);
        enVuelo.put(clave, futuro);

        try {
            return futuro.get(timeoutMs, TimeUnit.MILLISECONDS);

        } catch (TimeoutException e) {
            futuro.cancel(true);
            return caido(clave, nombre, tipo, ruta, info,
                "Sin respuesta en " + timeoutMs + " ms. El recurso esta inaccesible "
                + "o el enlace de red se corto.",
                "timeout de la sonda", timeoutMs);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return caido(clave, nombre, tipo, ruta, info,
                "Verificacion interrumpida.", e.toString(), 0);

        } catch (Exception e) {
            Throwable causa = e.getCause() != null ? e.getCause() : e;
            return caido(clave, nombre, tipo, ruta, info,
                "Error al verificar: " + causa.getMessage(),
                causa.getClass().getSimpleName() + ": " + causa.getMessage(), 0);
        }
    }

    private EstadoConexionDto caido(String clave, String nombre, String tipo, String ruta,
                                    InfoMontaje info, String detalle, String error, long latencia) {
        return new EstadoConexionDto(clave, nombre, tipo, EstadoConexionDto.CAIDO,
            detalle, info != null ? info.origen() : null, ruta,
            info != null ? info.montado() : null,
            0, false, latencia, null, ahora(), error);
    }

    // =========================================================================
    //  LECTURA DE /proc/mounts
    // =========================================================================

    /**
     * @param montado null si no se pudo determinar (por ejemplo, en Windows no
     *                existe /proc/mounts), true/false en Linux.
     * @param origen  el share remoto, ej. //172.16.21.4/dbfs
     * @param tipoFs  cifs, ext4, ...
     */
    private record InfoMontaje(Boolean montado, String origen, String tipoFs) {}

    private InfoMontaje leerInfoMontaje(String ruta) {
        Path procMounts = Path.of("/proc/mounts");
        if (!Files.isReadable(procMounts)) {
            return new InfoMontaje(null, null, null);   // no es Linux: no opinamos
        }
        try {
            String normalizada = (ruta.length() > 1 && ruta.endsWith("/"))
                               ? ruta.substring(0, ruta.length() - 1) : ruta;

            for (String linea : Files.readAllLines(procMounts)) {
                String[] campos = linea.split("\\s+");
                if (campos.length < 3) continue;
                // /proc/mounts escapa los espacios como \040
                String punto = campos[1].replace("\\040", " ");
                if (punto.equals(normalizada)) {
                    return new InfoMontaje(true, campos[0].replace("\\040", " "), campos[2]);
                }
            }
            return new InfoMontaje(false, null, null);

        } catch (Exception e) {
            log.debug("No se pudo leer /proc/mounts: {}", e.getMessage());
            return new InfoMontaje(null, null, null);
        }
    }

    // =========================================================================
    //  UTILIDADES
    // =========================================================================

    private long ms(long t0Nanos) {
        return (System.nanoTime() - t0Nanos) / 1_000_000;
    }

    private String ahora() {
        return FMT.format(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
    }

    /** jdbc:postgresql://host:5432/bd_a3?params -> host:5432/bd_a3 */
    private String resumirJdbc(String url) {
        if (url == null || url.isBlank()) return "-";
        String limpia = url.replaceFirst("^jdbc:[a-z]+://", "");
        int corte = limpia.indexOf('?');
        return corte > 0 ? limpia.substring(0, corte) : limpia;
    }
}
