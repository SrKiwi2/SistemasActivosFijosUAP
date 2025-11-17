package com.usic.SistemasActivosFijosUAP.controller.entidad;

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
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.EntidadDbf;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/entidad")
@RequiredArgsConstructor
public class EntidadController {
    private final IEntidadService entidadService;
    private final JavaDbfService dbfService;
    private final SyncControlService syncControlService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioEntidad() {
        return "entidad/vista";
    }

    // LISTA: intenta BD, si vacío cae a DBF
    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistrosEntidad(
            @RequestParam(name="q", required=false) String q,
            @RequestParam(name="gestion", required=false) Short gestion,
            Model model) throws Exception {

        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("entidad");
            
            if (syncInfo != null) {
                // Formatear la fecha para mostrar
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
        List<Entidad> listasEntidades = entidadService.buscarPorQ(q);
        boolean fromDb = listasEntidades != null && !listasEntidades.isEmpty();

        List<String> encryptedIds = new ArrayList<>();
        if (fromDb) {
            for (Entidad e : listasEntidades) {
                encryptedIds.add(Encriptar.encrypt(Long.toString(e.getIdEntidad())));
            }
            model.addAttribute("listasEntidades", listasEntidades);
            model.addAttribute("id_encryptado", encryptedIds);
            model.addAttribute("sourceUsed", "db");
            return "entidad/tabla_registro";
        }

        // 2) Fallback DBF
        var dbf = dbfService.listarEntidadesAll(gestion, q);
        // mapea a Entidad (solo para pintar la misma tabla; idEntidad = null → sin editar/borrar)
        List<Entidad> vm = new ArrayList<>(dbf.size());
        for (var d : dbf) {
            Entidad e = new Entidad();
            e.setIdEntidad(null); // importante para deshabilitar botones
            e.setGestion(d.getGestion());
            e.setEntidadCodigo(d.getEntidadCodigo());
            e.setDescripcion(d.getDescripcion());
            e.setSigla(d.getSigla());
            e.setSectorEnt(d.getSectorEnt());
            e.setSubsecEnt(d.getSubsecEnt());
            e.setAreaEnt(d.getAreaEnt());
            e.setSubareaEnt(d.getSubareaEnt());
            e.setNivelInst(d.getNivelInst());
            vm.add(e);
        }
        // ids vacíos para mantener el tamaño del arreglo
        for (int i=0; i<vm.size(); i++) encryptedIds.add("");

        model.addAttribute("listasEntidades", vm);
        model.addAttribute("id_encryptado", encryptedIds);
        model.addAttribute("sourceUsed", "dbf");
        return "entidad/tabla_registro";
    }

    // SYNC: importar DBF → BD (upsert por gestion+entidad_codigo)
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name="q", required=false) String q,
            @RequestParam(name="gestion", required=false) Short gestion,
            @RequestParam(name="forzarCompleto", defaultValue="false") boolean forzarCompleto) {
        
        long inicio = System.currentTimeMillis();
        
        try {
            // Obtener registros del DBF
            var registros = dbfService.listarEntidadesAll(gestion, q);
            
            // Cargar claves existentes en memoria (OPTIMIZACIÓN CLAVE)
            Map<String, Entidad> entidadesExistentes = cargarEntidadesExistentesEnCache(gestion);
            
            int inserted = 0, updated = 0, skipped = 0;
            List<Entidad> batch = new ArrayList<>(500);
            
            for (var dbfRecord : registros) {
                String clave = dbfRecord.getGestion() + "-" + dbfRecord.getEntidadCodigo();
                Entidad entidadExistente = entidadesExistentes.get(clave);
                
                // Crear/actualizar entidad
                Entidad entidad;
                boolean esNueva = (entidadExistente == null);
                
                if (esNueva) {
                    entidad = new Entidad();
                } else {
                    entidad = entidadExistente;
                }
                
                // Mapear datos del DBF
                mapearDatosDbfAEntidad(dbfRecord, entidad);
                
                // OPTIMIZACIÓN: Calcular hash y comparar
                String nuevoHash = entidad.calcularHash();
                
                if (!esNueva && !forzarCompleto) {
                    // Verificar si realmente cambió
                    if (nuevoHash.equals(entidad.getHashDatos())) {
                        skipped++;
                        continue; // NO procesar si no hay cambios
                    }
                }
                
                // Actualizar metadatos
                entidad.setHashDatos(nuevoHash);
                entidad.setFechaUltimaSync(LocalDateTime.now());
                
                batch.add(entidad);
                if (esNueva) inserted++; else updated++;
                
                // Procesar en lotes
                if (batch.size() >= 500) {
                    entidadService.saveAll(batch);
                    batch.clear();
                }
            }
            
            // Guardar lote final
            if (!batch.isEmpty()) {
                entidadService.saveAll(batch);
                batch.clear();
            }
            
            // Registrar sincronización
            long duracion = System.currentTimeMillis() - inicio;
            syncControlService.registrarSincronizacion("entidad", registros.size(), inserted, updated, duracion);
            
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalLeidas", registros.size(),
                "insertados", inserted,
                "actualizados", updated,
                "omitidos", skipped,
                "duracionMs", duracion,
                "mensaje", String.format("Sincronización completada en %.2f segundos", duracion/1000.0)
            ));
            
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando: " + ex.getMessage()
            ));
        }
    }

    // OPTIMIZACIÓN: Cargar todas las claves existentes en un Map (1 sola consulta)
    private Map<String, Entidad> cargarEntidadesExistentesEnCache(Short gestion) {
        List<Entidad> todas = (gestion != null) 
            ? entidadService.findByGestion(gestion)
            : entidadService.findAll();
        
        return todas.stream()
            .collect(Collectors.toMap(
                e -> e.getGestion() + "-" + e.getEntidadCodigo(),
                e -> e
            ));
    }
    
    private void mapearDatosDbfAEntidad(EntidadDbf dbf, Entidad entidad) {
        entidad.setGestion(dbf.getGestion());
        entidad.setEntidadCodigo(dbf.getEntidadCodigo());
        entidad.setDescripcion(dbf.getDescripcion());
        entidad.setSigla(dbf.getSigla());
        entidad.setSectorEnt(dbf.getSectorEnt());
        entidad.setSubsecEnt(dbf.getSubsecEnt());
        entidad.setAreaEnt(dbf.getAreaEnt());
        entidad.setSubareaEnt(dbf.getSubareaEnt());
        entidad.setNivelInst(dbf.getNivelInst());
    }
}