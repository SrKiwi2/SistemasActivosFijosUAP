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

    // Para evitar procesar eventos duplicados por cambios rápidos o archivos temporales:
    private final Map<String, Long> ultimoEventoMs = new ConcurrentHashMap<>();

    // Cooldown mínimo entre eventos del mismo archivo (ms)
    // DBFs pequeños: 30s | ACTUAL.DBF puede tener 60s por su scheduler propio
    @Value("${sync.poll.cooldown.ms:30000}")
    private long cooldownMs;

    // Mapa: nombre_tabla → ruta relativa dentro del directorio CIFS
    // Ajusta las rutas según la estructura real de tu DBF
    private final Map<String, String> archivosMonitoreados = new LinkedHashMap<>() {{
        put("entidad",             "entidades.DBF");
        put("predio",              "unidadadmin.DBF");
        put("grupoContable",       "CODCONT.DBF");
        put("organismoFinanciero", "organismo_fin.DBF");
        put("auxiliar",            "AUXILIAR.DBF");
        put("oficina",             "OFICINA.DBF");
        put("responsable",         "RESP.DBF");
        put("activo",              "ACTUAL.DBF");
    }};

    @PostConstruct
    public void inicializar() {
        log.info("DbfChangeDetectorService iniciado. Monitoreando: {}", dbfBasePath);
        for (var entry : archivosMonitoreados.entrySet()) {
            try {
                FileState estado = leerEstadoArchivo(entry.getValue());
                if (estado != null) {
                    estadosConocidos.put(entry.getKey(), estado);
                    log.info("  ✓ Baseline: {} ({} bytes, ts={})",
                        entry.getKey(), estado.sizeBytes(), estado.lastModifiedMs());
                }
            } catch (Exception e) {
                log.warn("  ✗ No se pudo leer baseline de {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${sync.poll.interval.ms:20000}",
               initialDelayString = "${sync.poll.initial.delay.ms:15000}")
    public void detectarCambios() {
        for (var entry : archivosMonitoreados.entrySet()) {
            if ("activo".equals(entry.getKey())) continue;
            verificarArchivo(entry.getKey(), entry.getValue());
        }
    }

    @Scheduled(fixedDelayString = "${sync.poll.activo.interval.ms:60000}",
               initialDelayString = "30000")
    public void detectarCambiosActivo() {
        verificarArchivo("activo", "ACTUAL.DBF");
    }

    private void verificarArchivo(String tabla, String rutaRelativa) {
        try {
            FileState estadoActual = leerEstadoArchivo(rutaRelativa);
            if (estadoActual == null) return;

            FileState estadoAnterior = estadosConocidos.get(tabla);
            if (estadoAnterior == null) {
                estadosConocidos.put(tabla, estadoActual);
                return;
            }

            if (!estadoActual.hasChangedFrom(estadoAnterior)) return;

            // ⭐ Cooldown: no re-disparar si ya se publicó un evento reciente
            long ahora = System.currentTimeMillis();
            Long ultimoEvento = ultimoEventoMs.get(tabla);
            if (ultimoEvento != null && (ahora - ultimoEvento) < cooldownMs) {
                log.debug("⏸ Cooldown activo para '{}', omitiendo evento duplicado.", tabla);
                // Actualizar el estado para no perder el cambio real
                estadosConocidos.put(tabla, estadoActual);
                return;
            }

            log.info("📦 Cambio en '{}': {}→{} bytes | ts: {}→{}",
                tabla,
                estadoAnterior.sizeBytes(), estadoActual.sizeBytes(),
                estadoAnterior.lastModifiedMs(), estadoActual.lastModifiedMs());

            estadosConocidos.put(tabla, estadoActual);
            ultimoEventoMs.put(tabla, ahora);

            eventPublisher.publishEvent(DbfChangeEvent.builder()
                .source(this).tabla(tabla)
                .rutaDbf(Path.of(dbfBasePath, rutaRelativa).toString())
                .estadoAnterior(estadoAnterior).estadoActual(estadoActual)
                .build());

        } catch (Exception e) {
            log.warn("Error verificando '{}': {}", tabla, e.getMessage());
        }
    }

    private FileState leerEstadoArchivo(String rutaRelativa) {
        try {
            Path archivo = Path.of(dbfBasePath, rutaRelativa);
            Path directorio = archivo.getParent();
            if (directorio != null) {
                try { directorio.toFile().list(); } catch (Exception ignored) {}
            }
            if (!Files.exists(archivo)) return null;
            BasicFileAttributes attrs = Files.readAttributes(archivo, BasicFileAttributes.class);
            return new FileState(attrs.lastModifiedTime().toMillis(), attrs.size());
        } catch (IOException e) {
            return null;
        }
    }
}
