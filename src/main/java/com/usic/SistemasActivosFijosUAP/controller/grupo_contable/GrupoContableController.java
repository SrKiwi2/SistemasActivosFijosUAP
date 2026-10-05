package com.usic.SistemasActivosFijosUAP.controller.grupo_contable;

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
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.GrupoContableDbf;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/grupoc")
@RequiredArgsConstructor
public class GrupoContableController {

    private final IGrupoContableService grupoContableService;
    private final JavaDbfService dbfService;
    private final SyncControlService syncControlService;
    /** Sin VSIAF a la vista (laptop de desarrollo, montaje caído) la sincronización avisa en vez de leer 0 registros. */
    private final com.usic.SistemasActivosFijosUAP.componet.VsiafDisponibilidad vsiaf;

    /**
     * La pantalla llega con la tabla y el estado de la sincronización ya armados: un solo
     * pedido al abrir. /tabla-registros queda para recargar (después de sincronizar o por SSE).
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioGrupoContable(Model model) throws Exception {
        cargarTabla(null, model);
        return "grupoContable/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(
            @RequestParam(name = "q", required = false) String q,
            Model model) throws Exception {
        cargarTabla(q, model);
        return "grupoContable/tabla_registro";
    }

    private void cargarTabla(String q, Model model) throws Exception {
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("grupo_contable");
            
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

        // 1) Intentar desde BD
        List<GrupoContable> bd = (q == null || q.isBlank())
                ? grupoContableService.listarGruposContables()
                : grupoContableService.buscarPorNombreLike("%" + q.trim() + "%");

        boolean fromDb = (bd != null && !bd.isEmpty());

        if (fromDb) {
            // Mapea a la forma que espera tu fragmento
            var listasGrupoContable = bd.stream().map(gc -> GrupoContableDbf.builder()
                    .codContable(gc.getCodContable() == null ? null : gc.getCodContable().longValue())
                    .nombre(gc.getNombre())
                    .vidaUtil(gc.getVidaUtil())
                    .depreciar(gc.getDepreciar())
                    .actualizar(gc.getActualizar())
                    .idGrupoContable(gc.getIdGrupoContable())
                    .build()).toList();

            var encryptedIds = new ArrayList<String>();
            for (var g : listasGrupoContable) {
                encryptedIds.add(Encriptar.encrypt(String.valueOf(g.getIdGrupoContable())));
            }

            model.addAttribute("listasGrupoContable", listasGrupoContable);
            model.addAttribute("id_encryptado", encryptedIds);
            model.addAttribute("sourceUsed", "db");
            return;
        }

        // 2) Fallback: DBF montado
        var listasGrupoContable = dbfService.listarCodcontAll(q);
        var encryptedIds = new ArrayList<String>();
        for (var g : listasGrupoContable) {
            encryptedIds.add(Encriptar.encrypt(String.valueOf(g.getIdGrupoContable())));
        }

        model.addAttribute("listasGrupoContable", listasGrupoContable);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", "dbf");
    }

    // En GrupoContableController
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {
        
        long inicio = System.currentTimeMillis();
        if (!vsiaf.dbf("sincronización manual")) {
            return ResponseEntity.ok(Map.of("ok", false, "message", vsiaf.motivoDbf()));
        }
        
        try {
            // Leer DBF
            var registros = dbfService.listarCodcontAll(q);
            
            // Cargar grupos existentes en caché
            Map<Integer, GrupoContable> gruposExistentes = cargarGruposEnCache();
            
            int inserted = 0, updated = 0, skipped = 0;
            List<GrupoContable> batch = new ArrayList<>(500);

            for (var d : registros) {
                Integer cod = d.getCodContable() != null ? d.getCodContable().intValue() : null;
                
                // Validar clave obligatoria
                if (cod == null) {
                    continue;
                }

                // Buscar en caché
                GrupoContable g = gruposExistentes.get(cod);
                
                // Determinar si es nuevo
                boolean esNuevo = (g == null);
                
                if (esNuevo) {
                    g = new GrupoContable();
                    g.setCodContable(cod);
                }

                // Mapear datos
                g.setNombre(d.getNombre());
                g.setVidaUtil(d.getVidaUtil() != null ? d.getVidaUtil().intValue() : null);
                g.setDepreciar(Boolean.TRUE.equals(d.getDepreciar()));
                g.setActualizar(Boolean.TRUE.equals(d.getActualizar()));
                g.setEstado("ACTIVO");

                // OPTIMIZACIÓN: Calcular hash y comparar
                String nuevoHash = g.calcularHash();
                
                if (!esNuevo && !forzarCompleto) {
                    // Verificar si realmente cambió
                    if (nuevoHash.equals(g.getHashDatos())) {
                        skipped++;
                        continue; // ⭐ NO procesar si no hay cambios
                    }
                }

                // Actualizar metadatos
                g.setHashDatos(nuevoHash);
                g.setFechaUltimaSync(LocalDateTime.now());

                batch.add(g);
                if (esNuevo) inserted++; else updated++;

                //  Guardar en lotes
                if (batch.size() >= 500) {
                    grupoContableService.saveAll(batch);
                    batch.clear();
                }
            }
            
            // Guardar lote final
            if (!batch.isEmpty()) {
                grupoContableService.saveAll(batch);
                batch.clear();
            }

            // Registrar en sync_control
            long duracion = System.currentTimeMillis() - inicio;
            syncControlService.registrarSincronizacion("grupo_contable", registros.size(), inserted, updated, duracion);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalLeidas", registros.size(),
                "insertados", inserted,
                "actualizados", updated,
                "omitidos", skipped,
                "duracionMs", duracion,
                "mensaje", String.format("Sincronización completada en %.2f segundos", duracion / 1000.0)
            ));
            
        } catch (Exception e) {
            // Registrar error
            syncControlService.registrarError("grupo_contable", e.getMessage());
            
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando desde DBF montado: " + e.getMessage()
            ));
        }
    }

    /**
     * OPTIMIZACIÓN: Cargar todos los grupos en memoria (1 sola consulta)
     */
    private Map<Integer, GrupoContable> cargarGruposEnCache() {
        List<GrupoContable> todos = grupoContableService.findAll();
        
        return todos.stream()
            .filter(g -> g.getCodContable() != null)
            .collect(Collectors.toMap(
                GrupoContable::getCodContable,
                g -> g,
                (existing, replacement) -> existing
            ));
    }

}