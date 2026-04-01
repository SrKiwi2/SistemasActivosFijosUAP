package com.usic.SistemasActivosFijosUAP.componet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.config.sincronizacion.FileState;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DbfChangeDetectorService {
    
    private final ApplicationEventPublisher eventPublisher;

    @Value("${legacy.dbf.path:/mnt/dbfwin}")
    private String dbfBasePath;

    // Estado conocido por cada tabla (tabla → FileState)
    private final Map<String, FileState> estadosConocidos = new ConcurrentHashMap<>();

    // Mapa: nombre_tabla → ruta relativa dentro del directorio CIFS
    // Ajusta las rutas según la estructura real de tu DBF
    private final Map<String, String> archivosMonitoreados = new LinkedHashMap<>() {{
        put("auxiliar",             "AUXILIAR.DBF");
        put("predio",               "PREDIO.DBF");
        put("entidad",              "ENTIDAD.DBF");
        put("grupoContable",        "GRUCONT.DBF");
        put("oficina",              "OFICINA.DBF");
        put("organismoFinanciero",  "ORGANIS.DBF");
        put("responsable",          "RESPONS.DBF");
    }};

    @PostConstruct
    public void inicializar() {
        log.info("DbfChangeDetectorService iniciado. Monitoreando: {}", dbfBasePath);
        // Captura estado inicial para no disparar sync en el arranque
        for (var entry : archivosMonitoreados.entrySet()) {
            try {
                FileState estado = leerEstadoArchivo(entry.getValue());
                if (estado != null) {
                    estadosConocidos.put(entry.getKey(), estado);
                    log.info("  ✓ Baseline capturado: {} ({} bytes)", 
                        entry.getKey(), estado.sizeBytes());
                }
            } catch (Exception e) {
                log.warn("  ✗ No se pudo leer baseline de {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * Polling cada 20 segundos. Ajusta según tus necesidades.
     * fixedDelay: espera 20s DESPUÉS de que termine la ejecución anterior.
     * Así no se acumulan ejecuciones si una tarda mucho.
     */
    @Scheduled(fixedDelayString = "${sync.poll.interval.ms:20000}",
               initialDelayString = "${sync.poll.initial.delay.ms:15000}")
    public void detectarCambios() {
        for (var entry : archivosMonitoreados.entrySet()) {
            String tabla = entry.getKey();
            String rutaRelativa = entry.getValue();

            try {
                FileState estadoActual = leerEstadoArchivo(rutaRelativa);
                if (estadoActual == null) continue;

                FileState estadoAnterior = estadosConocidos.get(tabla);

                if (estadoAnterior == null) {
                    // Primera vez que lo vemos después de arranque
                    estadosConocidos.put(tabla, estadoActual);
                    continue;
                }

                if (estadoActual.hasChangedFrom(estadoAnterior)) {
                    log.info("📦 Cambio detectado en DBF '{}': {} bytes → {} bytes",
                        tabla, estadoAnterior.sizeBytes(), estadoActual.sizeBytes());

                    // Actualizar estado conocido ANTES de publicar el evento
                    // para no dispararlo dos veces si la sync tarda
                    estadosConocidos.put(tabla, estadoActual);

                    // Publicar evento de forma desacoplada
                    eventPublisher.publishEvent(
                        DbfChangeEvent.builder()
                            .source(this)
                            .tabla(tabla)
                            .rutaDbf(Path.of(dbfBasePath, rutaRelativa).toString())
                            .estadoAnterior(estadoAnterior)
                            .estadoActual(estadoActual)
                            .build()
                    );
                }

            } catch (Exception e) {
                log.warn("Error verificando DBF '{}': {}", tabla, e.getMessage());
            }
        }
    }

    /**
     * Lee lastModified y size del archivo.
     * El listado del directorio padre fuerza refresco de caché CIFS.
     */
    private FileState leerEstadoArchivo(String rutaRelativa) {
        try {
            Path archivo = Path.of(dbfBasePath, rutaRelativa);

            // ⭐ Trick CIFS: listar directorio padre invalida caché de atributos
            Path directorio = archivo.getParent();
            if (directorio != null) {
                try {
                    directorio.toFile().list(); // fuerza refresco CIFS
                } catch (Exception ignored) {}
            }

            if (!Files.exists(archivo)) {
                return null;
            }

            BasicFileAttributes attrs = Files.readAttributes(archivo, BasicFileAttributes.class);
            return new FileState(
                attrs.lastModifiedTime().toMillis(),
                attrs.size()
            );
        } catch (IOException e) {
            return null;
        }
    }
}
