package com.usic.SistemasActivosFijosUAP.controller.organismoFinanciador;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/organismo")
@RequiredArgsConstructor
public class OrganismoFinanciadorController {

    private final IOrganismoFinancieroService organismoFinancieroService;
    private final JavaDbfService dbfService;
    private final SyncControlService syncControlService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_of() {
        return "organismoFinanciador/vista";
    }

    // Lista: primero BD; si vacío, muestra DBF (solo lectura)
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_of(Model model,
            @RequestParam(name = "q", required = false) String q) throws Exception {
        
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("organismo_financiero");
            
            if (syncInfo != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                String fechaFormateada = syncInfo.getUltimaSincronizacion().format(formatter);
                
                model.addAttribute("ultimaSincronizacion", fechaFormateada);
                model.addAttribute("estadoSync", syncInfo.getEstado());
                model.addAttribute("registrosProcesados", syncInfo.getRegistrosProcesados());
                model.addAttribute("registrosNuevos", syncInfo.getRegistrosNuevos());
                model.addAttribute("registrosActualizados", syncInfo.getRegistrosActualizados());
                model.addAttribute("duracionUltimaSync", syncInfo.getDuracionMs() / 1000.0);
            } else {
                model.addAttribute("ultimaSincronizacion", "Nunca sincronizado");
                model.addAttribute("estadoSync", "PENDIENTE");
            }
        } catch (Exception e) {
            model.addAttribute("ultimaSincronizacion", "Error al obtener info");
            model.addAttribute("estadoSync", "ERROR");
        }

        List<OrganismoFinanciero> lista = (q == null || q.isBlank())
                ? organismoFinancieroService.findAll()
                : organismoFinancieroService.buscarPorQ(q);

        boolean fromDb = lista != null && !lista.isEmpty();
        List<String> encryptedIds = new ArrayList<>();

        if (fromDb) {
            for (var of : lista) {
                encryptedIds.add(Encriptar.encrypt(String.valueOf(of.getIdOrganismoFinanciero())));
            }
            model.addAttribute("listasOrganismoFinanciero", lista);
            model.addAttribute("id_encryptado", encryptedIds);
            model.addAttribute("sourceUsed", "db");
            return "organismoFinanciador/tabla_registro";
        }

        // Fallback: DBF
        var filas = dbfService.listarOrganismoFinAll(q);
        // Mapea DTO→Entidad-like (id nulo)
        var fantasma = new ArrayList<OrganismoFinanciero>(filas.size());
        for (var f : filas) {
            var of = new OrganismoFinanciero();
            of.setIdOrganismoFinanciero(null);
            of.setGestion(f.getGestion());
            of.setCodOf(f.getCodOf());
            of.setDescripcion(f.getDescripcion());
            of.setSigla(f.getSigla());
            of.setEstado("ACTIVO");
            fantasma.add(of);
            encryptedIds.add("");
        }
        model.addAttribute("listasOrganismoFinanciero", fantasma);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", "dbf");
        return "organismoFinanciador/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {
        
        long inicio = System.currentTimeMillis();
        
        try {
            // Leer DBF
            var filas = dbfService.listarOrganismoFinAll(q);
            
            // Cargar organismos existentes en caché
            Map<String, OrganismoFinanciero> organismosExistentes = cargarOrganismosEnCache();
            
            int inserted = 0, updated = 0, skipped = 0, repetidosDbf = 0;
            List<OrganismoFinanciero> batch = new ArrayList<>(500);
            Set<String> seen = new HashSet<>(filas.size());

            for (var f : filas) {
                // Validar campos obligatorios
                if (f.getGestion() == null || isBlank(f.getCodOf()) || isBlank(f.getDescripcion())) {
                    continue;
                }

                // Detectar duplicados en el DBF
                String key = f.getGestion() + "|" + f.getCodOf().trim().toUpperCase();
                if (!seen.add(key)) {
                    repetidosDbf++;
                    continue;
                }

                // Buscar en caché (por gestion-codOf)
                OrganismoFinanciero of = organismosExistentes.get(key);
                
                // Si no existe, intentar por sigla
                if (of == null && f.getSigla() != null && !f.getSigla().isBlank()) {
                    String keySigla = f.getGestion() + "|SIGLA|" + f.getSigla().trim().toUpperCase();
                    of = organismosExistentes.get(keySigla);
                }

                // Determinar si es nuevo
                boolean esNuevo = (of == null);
                
                if (esNuevo) {
                    of = new OrganismoFinanciero();
                    of.setGestion(f.getGestion());
                    of.setCodOf(f.getCodOf().trim());
                } else {
                    // Si vino por sigla y codOf difiere, actualizar codOf
                    if (f.getCodOf() != null && !f.getCodOf().isBlank()) {
                        of.setCodOf(f.getCodOf().trim());
                    }
                }

                // Mapear datos
                of.setDescripcion(f.getDescripcion().trim());
                of.setSigla(f.getSigla() == null ? null : f.getSigla().trim());
                of.setEstado("ACTIVO");

                // OPTIMIZACIÓN: Calcular hash y comparar
                String nuevoHash = of.calcularHash();
                
                if (!esNuevo && !forzarCompleto) {
                    // Verificar si realmente cambió
                    if (nuevoHash.equals(of.getHashDatos())) {
                        skipped++;
                        continue; // ⭐ NO procesar si no hay cambios
                    }
                }

                // actualizar metadatos
                of.setHashDatos(nuevoHash);
                of.setFechaUltimaSync(LocalDateTime.now());

                batch.add(of);
                if (esNuevo) inserted++; else updated++;

                // Guardar en lotes
                if (batch.size() >= 500) {
                    organismoFinancieroService.saveAll(batch);
                    batch.clear();
                }
            }
            
            //Guardar lote final
            if (!batch.isEmpty()) {
                organismoFinancieroService.saveAll(batch);
                batch.clear();
            }

            // Registrar en sync_control
            long duracion = System.currentTimeMillis() - inicio;
            syncControlService.registrarSincronizacion("organismo_financiero", filas.size(), inserted, updated, duracion);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalLeidas", filas.size(),
                "insertados", inserted,
                "actualizados", updated,
                "omitidos", skipped,
                "duplicadosEnDbf", repetidosDbf,
                "duracionMs", duracion,
                "mensaje", String.format("Sincronización completada en %.2f segundos", duracion / 1000.0)
            ));
            
        } catch (Exception ex) {
            // Registrar error
            syncControlService.registrarError("organismo_financiero", ex.getMessage());
            
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando ORGANISMO_FIN: " + ex.getMessage()
            ));
        }
    }

    /**
     * OPTIMIZACIÓN: Cargar todos los organismos en memoria (1 sola consulta)
     */
    private Map<String, OrganismoFinanciero> cargarOrganismosEnCache() {
        List<OrganismoFinanciero> todos = organismoFinancieroService.findAll();
        
        Map<String, OrganismoFinanciero> cache = new HashMap<>();
        
        for (OrganismoFinanciero of : todos) {
            // Clave principal: gestion|codOf
            String key = of.getGestion() + "|" + of.getCodOf().trim().toUpperCase();
            cache.put(key, of);
            
            // Clave alternativa por sigla (si existe)
            if (of.getSigla() != null && !of.getSigla().isBlank()) {
                String keySigla = of.getGestion() + "|SIGLA|" + of.getSigla().trim().toUpperCase();
                cache.putIfAbsent(keySigla, of); // No sobrescribir si ya existe
            }
        }
        
        return cache;
    }

    /**
     * ENDPOINT AJAX para obtener info de sincronización
     */
    @GetMapping("/sync-info")
    @ResponseBody
    public ResponseEntity<?> obtenerInfoSync() {
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("organismo_financiero");
            
            if (syncInfo != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
                
                return ResponseEntity.ok(Map.of(
                    "ultimaSincronizacion", syncInfo.getUltimaSincronizacion().format(formatter),
                    "estado", syncInfo.getEstado(),
                    "registrosProcesados", syncInfo.getRegistrosProcesados(),
                    "registrosNuevos", syncInfo.getRegistrosNuevos(),
                    "registrosActualizados", syncInfo.getRegistrosActualizados(),
                    "duracionSegundos", syncInfo.getDuracionMs() / 1000.0
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "ultimaSincronizacion", "Nunca sincronizado",
                "estado", "PENDIENTE",
                "registrosProcesados", 0,
                "registrosNuevos", 0,
                "registrosActualizados", 0,
                "duracionSegundos", 0.0
            ));
            
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                "ultimaSincronizacion", "Error al obtener info",
                "estado", "ERROR",
                "registrosProcesados", 0,
                "registrosNuevos", 0,
                "registrosActualizados", 0,
                "duracionSegundos", 0.0
            ));
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}