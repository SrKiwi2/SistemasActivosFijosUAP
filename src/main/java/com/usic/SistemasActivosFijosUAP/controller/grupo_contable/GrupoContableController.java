package com.usic.SistemasActivosFijosUAP.controller.grupo_contable;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.GrupoContableDbf;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/grupoc")
@RequiredArgsConstructor
public class GrupoContableController {

    private final IGrupoContableService grupoContableService;
    private final JavaDbfService dbfService;
    private final SyncControlService syncControlService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioGrupoContable() {
        return "grupoContable/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros(
            @RequestParam(name = "q", required = false) String q,
            Model model) throws Exception {

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
            return "grupoContable/tabla_registro";
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
        return "grupoContable/tabla_registro";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioGrupoContable(Model model, GrupoContable grupoContable) {
        return "grupoContable/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_grupo_contable}")
    public String formularioEditGrupoContable(Model model, @PathVariable("id_grupo_contable") String idGrupoContable)
            throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idGrupoContable));
        model.addAttribute("grupoContable", grupoContableService.findById(id));
        model.addAttribute("edit", "true");
        return "grupoContable/formulario";
    }

    /* para ahcer registros al archivo dbf de windows */
    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-grupoc")
    public ResponseEntity<String> registrarGrupoContableBDF(GrupoContable grupoContable,
            RedirectAttributes ra, HttpServletRequest request) {
        try {
            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            // 1) Datos del formulario
            String nombre = grupoContable.getNombre();
            String codigoStr = String.valueOf(grupoContable.getCodContable()).trim();

            if (nombre == null || nombre.isBlank() || codigoStr.isBlank()) {
                ra.addFlashAttribute("error", "Nombre y Código son obligatorios.");
                return ResponseEntity.ok("Ha ocurrido un error en el registro");
            }

            short codcont = Short.parseShort(codigoStr);

            // 2) Defaults (ajusta a tus reglas)
            short vidautil = 5;
            String observ = "";
            boolean depreciar = true;
            boolean actualizar = true;
            LocalDate feult = LocalDate.now();
            String usuar = (usuario.getUsuario());

            // 3) Insertar en DBF
            dbfService.insertCodcont(codcont, nombre, vidautil, observ, depreciar, actualizar, feult, usuar);

            ra.addFlashAttribute("ok", "Registrado en CODCONT.DBF: " + codcont + " - " + nombre);
        } catch (NumberFormatException nfe) {
            ra.addFlashAttribute("error", "El código debe ser numérico (SmallInt).");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error insertando en CODCONT.DBF: " + e.getMessage());
        }
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @PostMapping(value = "/modificar-grupoc")
    public ResponseEntity<String> modificarGrupoContable(HttpServletRequest request, GrupoContable grupoContable,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        grupoContable.setModificacionIdUsuario(usuario.getIdUsuario());
        grupoContable.setEstado("ACTIVO");
        grupoContableService.save(grupoContable);
        return ResponseEntity.ok("Se realizó el registro correctamente");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_grupo_contable}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_grupo_contable") String idGrupoContable)
            throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idGrupoContable));
        GrupoContable grupoContable = grupoContableService.findById(id);
        grupoContable.setEstado("ELIMINADO");
        grupoContableService.save(grupoContable);
        return ResponseEntity.ok("Registro Eliminado");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/importar-dbf")
    public ResponseEntity<String> importarDesdeDBF(@RequestParam("archivo") MultipartFile archivo) {
        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body("Archivo no proporcionado.");
        }

        try {
            // Convertir MultipartFile a File temporal
            File tempFile = File.createTempFile("grupo_contable_", ".dbf");
            archivo.transferTo(tempFile);

            grupoContableService.importarDesdeDBF(tempFile);
            return ResponseEntity.ok("Importación completada con éxito.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error durante la importación: " + e.getMessage());
        }
    }

    // En GrupoContableController
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {
        
        long inicio = System.currentTimeMillis();
        
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

    /**
     * ENDPOINT AJAX para obtener info de sincronización
     */
    @GetMapping("/sync-info")
    @ResponseBody
    public ResponseEntity<?> obtenerInfoSync() {
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("grupo_contable");
            
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