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
        put("predio",               "PREDIO.DBF");
        put("entidad",              "ENTIDAD.DBF");
        put("grupoContable",        "GRUCONT.DBF");
        put("organismoFinanciero",  "ORGANIS.DBF");
        put("auxiliar",             "AUXILIAR.DBF");
        put("oficina",              "OFICINA.DBF");
        put("responsable",          "RESP.DBF");

        put("activo",               "ACTUAL.DBF");
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

    @Scheduled(fixedDelayString = "${sync.poll.interval.ms:20000}",
           initialDelayString = "${sync.poll.initial.delay.ms:15000}")
    public void detectarCambios() {
        // Detectar cambios en todas las tablas EXCEPTO activo
        for (var entry : archivosMonitoreados.entrySet()) {
            if ("activo".equals(entry.getKey())) continue; // tiene su propio scheduler
            verificarArchivo(entry.getKey(), entry.getValue());
        }
    }

    @Scheduled(fixedDelayString = "${sync.poll.activo.interval.ms:60000}",
            initialDelayString = "${sync.poll.initial.delay.ms:30000}")
    public void detectarCambiosActivo() {
        verificarArchivo("activo", "ACTUAL.DBF");
    }

    // Extraer lógica común:
    private void verificarArchivo(String tabla, String rutaRelativa) {
        try {
            FileState estadoActual = leerEstadoArchivo(rutaRelativa);
            if (estadoActual == null) return;

            FileState estadoAnterior = estadosConocidos.get(tabla);
            if (estadoAnterior == null) {
                estadosConocidos.put(tabla, estadoActual);
                return;
            }

            if (estadoActual.hasChangedFrom(estadoAnterior)) {
                log.info("📦 Cambio en '{}': {}→{} bytes",
                    tabla, estadoAnterior.sizeBytes(), estadoActual.sizeBytes());
                estadosConocidos.put(tabla, estadoActual);
                eventPublisher.publishEvent(DbfChangeEvent.builder()
                    .source(this).tabla(tabla).rutaDbf(Path.of(dbfBasePath, rutaRelativa).toString())
                    .estadoAnterior(estadoAnterior).estadoActual(estadoActual).build());
            }
        } catch (Exception e) {
            log.warn("Error verificando '{}': {}", tabla, e.getMessage());
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
