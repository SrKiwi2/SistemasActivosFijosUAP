package com.usic.SistemasActivosFijosUAP.controller.predio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/predio")
@RequiredArgsConstructor
public class PredioController {
    private final IPredioServicio predioServicio;
    private final IEntidadService entidadService;
    private final JavaDbfService dbfService;
    private final SyncControlService syncControlService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_predio() {
        return "predio/vista";
    }

    // LISTA: intenta BD; si vacío, usa DBF montado (sólo lectura)
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(Model model,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) throws Exception {

        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("predio");
            
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

        // 1) BD
        List<Predio> listasPredios = predioServicio.buscarPorQ(q);
        boolean fromDb = listasPredios != null && !listasPredios.isEmpty();

        if (!fromDb) {

            var filas = dbfService.listarUnidadAdminAll(q);
            listasPredios = new ArrayList<>(filas.size());

            for (var f : filas) {

                Entidad ent = resolverEntidad(entidadService, gestionPreferida, f.getEntidadCodigo());

                Predio p = new Predio();
                p.setIdPredio(null);
                p.setEntidad(ent);
                p.setUnidad(f.getUnidad());
                p.setDescrip(f.getDescrip());
                p.setCiudad(f.getCiudad());
                p.setEstadoUni(f.getEstadoUni());
                p.setCodigo(f.getEntidadCodigo());
                p.setEstado("ACTIVO");

                listasPredios.add(p);
            }
        }

        List<String> encryptedIds = new ArrayList<>();
        for (Predio p : listasPredios) {
            encryptedIds.add(p.getIdPredio() == null ? "" : Encriptar.encrypt(Long.toString(p.getIdPredio())));
        }

        model.addAttribute("listasPredios", listasPredios);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", fromDb ? "db" : "dbf");
        return "predio/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {
        
        long inicio = System.currentTimeMillis();
        
        try {
            // Leer DBF
            var filas = dbfService.listarUnidadAdminAll(q);
            
            // Cargar predios existentes en caché (1 sola consulta)
            Map<String, Predio> prediosExistentes = cargarPrediosEnCache(gestionPreferida);
            
            int inserted = 0, updated = 0, skipped = 0, sinEntidad = 0;
            List<Predio> batch = new ArrayList<>(500);

            for (var f : filas) {
                // Validar claves obligatorias
                if (isBlank(f.getEntidadCodigo()) || isBlank(f.getUnidad())) {
                    skipped++;
                    continue;
                }

                // Resolver entidad
                Entidad entidad = resolverEntidad(entidadService, gestionPreferida, f.getEntidadCodigo());
                if (entidad == null) {
                    sinEntidad++;
                    continue;
                }

                // Crear clave única para búsqueda en caché
                String clave = entidad.getIdEntidad() + "-" + f.getUnidad().trim();
                Predio predioExistente = prediosExistentes.get(clave);
                
                // Determinar si es nuevo o actualización
                Predio predio;
                boolean esNuevo = (predioExistente == null);
                
                if (esNuevo) {
                    predio = new Predio();
                    predio.setEntidad(entidad);
                    predio.setUnidad(f.getUnidad().trim());
                } else {
                    predio = predioExistente;
                }

                // Mapear datos del DBF
                predio.setDescrip(f.getDescrip() != null ? f.getDescrip().trim() : "");
                predio.setCiudad(f.getCiudad() != null ? f.getCiudad().trim() : null);
                predio.setEstadoUni(f.getEstadoUni());
                predio.setEstado("ACTIVO");

                // OPTIMIZACIÓN: Calcular hash y comparar
                String nuevoHash = predio.calcularHash();
                
                if (!esNuevo && !forzarCompleto) {
                    // Verificar si realmente cambió
                    if (nuevoHash.equals(predio.getHashDatos())) {
                        skipped++;
                        continue; // NO procesar si no hay cambios
                    }
                }

                // Actualizar metadatos de sincronización
                predio.setHashDatos(nuevoHash);
                predio.setFechaUltimaSync(LocalDateTime.now());

                batch.add(predio);
                if (esNuevo) inserted++; else updated++;

                // Guardar en lotes de 500
                if (batch.size() >= 500) {
                    predioServicio.saveAll(batch);
                    batch.clear();
                }
            }
            
            // Guardar lote final
            if (!batch.isEmpty()) {
                predioServicio.saveAll(batch);
                batch.clear();
            }

            // Registrar en sync_control
            long duracion = System.currentTimeMillis() - inicio;
            syncControlService.registrarSincronizacion("predio", filas.size(), inserted, updated, duracion);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalLeidas", filas.size(),
                "insertados", inserted,
                "actualizados", updated,
                "omitidos", skipped,
                "sinEntidadEnBD", sinEntidad,
                "duracionMs", duracion,
                "mensaje", String.format("Sincronización completada en %.2f segundos", duracion / 1000.0)
            ));
            
        } catch (Exception ex) {
            // Registrar error
            syncControlService.registrarError("predio", ex.getMessage());
            
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando UNIDADADMIN: " + ex.getMessage()
            ));
        }
    }

    /**
     * OPTIMIZACIÓN: Cargar todos los predios en memoria (1 sola consulta SQL)
     */
    private Map<String, Predio> cargarPrediosEnCache(Short gestion) {
        List<Predio> todos;
        
        if (gestion != null) {
            // Filtrar por gestión de la entidad relacionada
            todos = predioServicio.findAll().stream()
                .filter(p -> p.getEntidad() != null && 
                           gestion.equals(p.getEntidad().getGestion()))
                .collect(Collectors.toList());
        } else {
            todos = predioServicio.findAll();
        }
        
        // Crear mapa con clave: "entidadId-unidad"
        return todos.stream()
            .collect(Collectors.toMap(
                p -> p.getEntidad().getIdEntidad() + "-" + p.getUnidad().trim(),
                p -> p,
                (existing, replacement) -> existing // En caso de duplicados, mantener el existente
            ));
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String stripLeftZeros(String s) {
        if (s == null)
            return null;
        String out = s.replaceFirst("^0+", "");
        return out.isEmpty() ? "0" : out;
    }

    private String leftPad4(String s) {
        String base = stripLeftZeros(s);
        try {
            int n = Integer.parseInt(base);
            return String.format("%04d", n);
        } catch (NumberFormatException e) {
            return s; // si no es numérico, deja como está
        }
    }

    /**
     * Intenta resolver por gestión preferida; si no, por la más reciente. Prueba 2
     * variantes: como viene y normalizada.
     */
    private Entidad resolverEntidad(IEntidadService entidadService, Short gestionPreferida, String codigo) {
        String cod = codigo;
        String codNoZeros = stripLeftZeros(codigo);
        String codPad4 = leftPad4(codigo); // por si en BD está siempre 4 dígitos

        // orden de prueba: tal cual -> sin ceros -> padded 4
        if (gestionPreferida != null) {
            return entidadService.findByGestionAndEntidadCodigo(gestionPreferida, cod)
                    .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codNoZeros))
                    .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codPad4))
                    .orElse(null);
        } else {
            return entidadService.findTopByEntidadCodigoOrderByGestionDesc(cod)
                    .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codNoZeros))
                    .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codPad4))
                    .orElse(null);
        }
    }

    /**
     * ENDPOINT AJAX para obtener info de sincronización
     */
    @GetMapping("/sync-info")
    @ResponseBody
    public ResponseEntity<?> obtenerInfoSync() {
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("predio");
            
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

}