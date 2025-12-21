package com.usic.SistemasActivosFijosUAP.controller.oficina;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.OficinaDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/oficina")
@RequiredArgsConstructor
public class OficinaController {

    private final IOficinaService oficinaService;
    private final IPredioServicio predioServicio;
    private final IEntidadService entidadService;
    private final JavaDbfService dbfService;
    private final OficinaDbfWriterService oficinaDbfWriterService;
    private final SyncControlService syncControlService;

    private static final Logger log = LoggerFactory.getLogger(OficinaController.class);

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_oficina(Model model) {

        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("oficina");
            
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

        return "oficina/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_oficina(Model model,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) {

        try {
            // 1. Cargar datos de la BD
            List<Oficina> listasOficinas = oficinaService.buscarPorQ(q);
            
            // 2. Comparación Cruzada (BD vs DBF)
            if (listasOficinas != null && !listasOficinas.isEmpty()) {
                
                // Leemos el DBF (El método blindado que ya tienes)
                var filasDbf = dbfService.listarOficinaAll(null); 
                
                log.info("📊 DEBUG: Registros BD: {} | Registros DBF Válidos: {}", listasOficinas.size(), filasDbf.size());

                // --- DEBUG: VER CÓMO SE GENERAN LAS CLAVES DEL DBF ---
                Set<String> clavesDbf = new HashSet<>();
                int debugCounter = 0;
                
                for (var f : filasDbf) {
                    String clave = generarClaveUnica(f.getEntidadCodigo(), f.getUnidad(), f.getCodOfi());
                    clavesDbf.add(clave);
                    
                    // Imprimir las primeras 3 claves del DBF para comparar visualmente
                    if (debugCounter < 3) {
                        log.info("🔑 CLAVE DBF [{}]: '{}'", debugCounter, clave);
                        debugCounter++;
                    }
                }

                // --- DEBUG: VER CÓMO SE GENERAN LAS CLAVES DE LA BD ---
                debugCounter = 0;
                for (Oficina o : listasOficinas) {
                    if (o.getPredio() != null && o.getPredio().getEntidad() != null) {
                        
                        String ent = o.getPredio().getEntidad().getEntidadCodigo();
                        
                        // Lógica: Usamos Código si existe, si no Unidad.
                        String uni = o.getPredio().getCodigo();
                        if (uni == null || uni.isBlank()) {
                            uni = o.getPredio().getUnidad();
                        }
                        
                        String claveBd = generarClaveUnica(ent, uni, o.getCodOfi());
                        
                        // Imprimir las primeras 3 claves de BD para comparar
                        if (debugCounter < 3) {
                            log.info("🗝️ CLAVE BD  [{}]: '{}'", debugCounter, claveBd);
                            debugCounter++;
                        }

                        // LA COMPARACIÓN REAL
                        if (clavesDbf.contains(claveBd)) {
                            o.setExisteEnDbf(true); 
                        } else {
                            o.setExisteEnDbf(false);
                            // Loguear el primer fallo para entender por qué no cruza
                            if (debugCounter == 3) { 
                                log.warn("⚠️ NO CRUZÓ: La clave BD '{}' no se halló en el Set del DBF.", claveBd);
                                debugCounter++;
                            }
                        }
                    } else {
                        o.setExisteEnDbf(false); 
                    }
                }
            }

            // 3. Encriptación IDs (Igual que antes)
            List<String> encryptedIds = new ArrayList<>();
            if (listasOficinas != null) {
                for (Oficina o : listasOficinas) {
                    encryptedIds.add(o.getIdOficina() == null ? "" : Encriptar.encrypt(Long.toString(o.getIdOficina())));
                }
            }

            model.addAttribute("listasOficinas", listasOficinas);
            model.addAttribute("id_encryptado", encryptedIds);
            model.addAttribute("sourceUsed", "db");

        } catch (Exception e) {
            log.error("Error cargando tabla oficinas", e);
            model.addAttribute("error", "Error cargando datos: " + e.getMessage());
        }
        
        return "oficina/tabla_registro";
    }

    // 🔴 MÉTODO DE CLAVE NORMALIZADA (AGRESIVO)
    private String generarClaveUnica(String entidad, String unidad, Short codOfi) {
        // 1. Manejo de nulos
        String e = (entidad == null) ? "" : entidad;
        String u = (unidad == null) ? "" : unidad;
        
        // 2. Limpieza agresiva: Mayúsculas, Trim y eliminar espacios invisibles raros
        e = e.trim().toUpperCase().replaceAll("\\p{C}", ""); // Quita caracteres de control
        u = u.trim().toUpperCase().replaceAll("\\p{C}", "");
        
        // 3. Código Numérico
        String c = (codOfi == null) ? "0" : String.valueOf(codOfi);
        
        // 4. Retorno: "148|CUSP|1"
        return e + "|" + u + "|" + c;
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formulario_oficina(Model model, Oficina oficina) {
        model.addAttribute("oficina", new Oficina());
        model.addAttribute("predios", predioServicio.findAll());
        return "oficina/formulario";
    }

    @GetMapping("/siguiente-codigo/{idPredio}")
    public ResponseEntity<Short> getSiguienteCodigoOficina(@PathVariable Long idPredio) {
        Short siguienteCodOfi = oficinaService.findNextCodOfiByPredioId(idPredio);
        return ResponseEntity.ok(siguienteCodOfi);
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_oficina}")
    public String formularioEdit_oficina(Model model, @PathVariable("id_oficina") String idOficina) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idOficina));
        model.addAttribute("oficina", oficinaService.findById(id));
        model.addAttribute("predios", predioServicio.findAll());
        model.addAttribute("edit", "true");
        return "oficina/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-oficina")
    @ResponseBody
    public ResponseEntity<?> registrar_oficina(
            HttpServletRequest request,
            @Validated @ModelAttribute Oficina oficina,
            BindingResult br) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        
        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
            ));
        }
        
        String usuarioNombre = usuario.getUsuario();
        
        oficina.setEstado("ACTIVO");
        oficina.setFechaUlt(LocalDate.now());
        oficina.setUsuario(usuarioNombre);
        oficina.setApiEstado(Short.valueOf("1"));
        if (usuario != null) {
            oficina.setRegistroIdUsuario(usuario.getIdUsuario());
        }

        Predio predioOficina = predioServicio.findById(oficina.getPredio().getIdPredio());
        Long idEntidadOficina = predioOficina.getEntidad().getIdEntidad();
        Entidad entidadOficina = entidadService.findById(idEntidadOficina);

        String entidadCode = entidadOficina.getEntidadCodigo();
        String unidadCode = predioOficina.getUnidad();
        
        if (oficina.getPredio() != null && oficina.getPredio().getCodigo() != null) {
            unidadCode = oficina.getPredio().getCodigo();
        }
        
        Short codOfic = oficina.getCodOfi();
        
        if (codOfic != null) {
            if (oficinaDbfWriterService.existsByCodOfic(codOfic, entidadCode, unidadCode)) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "msg", "Ya existe una oficina con CODOFIC=" + codOfic + " en el DBF"
                ));
            }
        }
        
        oficinaService.save(oficina);
        
        try {
            oficinaDbfWriterService.insertarDesdeOficina(oficina, entidadCode, unidadCode, usuarioNombre);
            log.info("Oficina {} registrada en PostgreSQL y DBF", oficina.getIdOficina());
        } catch (Exception e) {
            log.error("Error insertando oficina en DBF: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "ok", false,
                "msg", "Se guardó en la base de datos pero falló el registro en DBF: " + e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "msg", "Se realizó el registro correctamente en PostgreSQL y DBF",
            "id", oficina.getIdOficina()
        ));
    }


    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-oficina")
    @ResponseBody
    public ResponseEntity<?> modificar_oficina(
            HttpServletRequest request,
            @Validated @ModelAttribute Oficina oficinaForm,
            BindingResult br) {
        
        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
            ));
        }
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = usuario.getUsuario();
        
        Oficina oficinaOriginal = oficinaService.findById(oficinaForm.getIdOficina());
        if (oficinaOriginal == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "No se encontró la oficina con ID: " + oficinaForm.getIdOficina()
            ));
        }

        Predio predioOriginal = oficinaOriginal.getPredio();
        if (predioOriginal == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "Error: La oficina original no tiene un Predio asociado."
            ));
        }
        
        Entidad entidad = predioOriginal.getEntidad();
        if (entidad == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "Error: El Predio original no tiene una Entidad asociada."
            ));
        }

        if (oficinaForm.getPredio() == null || oficinaForm.getPredio().getIdPredio() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "Error: El formulario no proporciona el ID de Predio para la modificación."
            ));
        }

        Predio preido = predioServicio.findById(oficinaForm.getPredio().getIdPredio());
        if (preido == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "Error: El Predio con ID " + oficinaForm.getPredio().getIdPredio() + " no fue encontrado."
            ));
        }

        Short codOficOriginal = oficinaOriginal.getCodOfi();
        String entidadOriginal = entidad.getEntidadCodigo();
        String unidadOriginal = preido.getUnidad();
        
        oficinaOriginal.setPredio(oficinaForm.getPredio());
        oficinaOriginal.setCodOfi(oficinaForm.getCodOfi());
        oficinaOriginal.setNombre(oficinaForm.getNombre());
        oficinaOriginal.setObserv(oficinaForm.getObserv());
        oficinaOriginal.setFechaUlt(LocalDate.now());
        oficinaOriginal.setModificacion(new Date());
        oficinaOriginal.setUsuario(usuarioNombre);
        if (usuario != null) {
            oficinaOriginal.setModificacionIdUsuario(usuario.getIdUsuario());
        }
        oficinaOriginal.setEstado("ACTIVO");
        
        oficinaService.save(oficinaOriginal);
        
        try {
            String entidadCode = preido.getEntidad().getEntidadCodigo();
            String unidadCode = preido.getUnidad();
            
            oficinaDbfWriterService.actualizarDesdeOficina(
                codOficOriginal,
                entidadOriginal,
                unidadOriginal,
                oficinaOriginal,
                entidadCode,
                unidadCode,
                usuarioNombre
            );
            
            log.info("Oficina {} actualizada en PostgreSQL y DBF", oficinaOriginal.getIdOficina());
            
        } catch (Exception e) {
            log.error("Error actualizando oficina en DBF: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "ok", false,
                "msg", "Se guardó en la base de datos pero falló la actualización en DBF: " + e.getMessage()
            ));
        }
        
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "msg", "Se modificó correctamente en PostgreSQL y DBF"
        ));
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_oficina}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_oficina") String idOficina) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idOficina));
        Oficina oficina = oficinaService.findById(id);
        oficina.setEstado("ELIMINADO");
        oficinaService.save(oficina);
        return ResponseEntity.ok("Registro Eliminado");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/subir-dbf/{id_oficina}")
    @ResponseBody
    public ResponseEntity<?> subirOficinaADbf(HttpServletRequest request, 
                                              @PathVariable("id_oficina") String idOficinaEnc) {
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idOficinaEnc));
            Oficina oficina = oficinaService.findById(id);
            
            if (oficina == null) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Oficina no encontrada"));

            Predio predio = oficina.getPredio();
            String entidadCode = predio.getEntidad().getEntidadCodigo();
            String unidadCode = (predio.getCodigo() != null) ? predio.getCodigo() : predio.getUnidad();
            
            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

            // Usamos el WriterService (el que usa RandomAccessFile o DBFWriter)
            if (oficinaDbfWriterService.existsByCodOfic(oficina.getCodOfi(), entidadCode, unidadCode)) {
                 return ResponseEntity.ok(Map.of("ok", true, "msg", "La oficina ya existe en el DBF."));
            }

            // Insertar
            oficinaDbfWriterService.insertarDesdeOficina(oficina, entidadCode, unidadCode, usuarioNombre);

            return ResponseEntity.ok(Map.of("ok", true, "msg", "Oficina registrada correctamente en el DBF."));

        } catch (Exception e) {
            log.error("Error subiendo a DBF", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
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

            var filas = dbfService.listarOficinaAll(q);
            
            Map<String, Oficina> oficinasExistentes = cargarOficinasEnCache(gestionPreferida);
            
            int inserted = 0, updated = 0, skipped = 0, sinEntidad = 0, sinPredio = 0;
            List<Oficina> batch = new ArrayList<>(500);

            for (var f : filas) {

                Entidad entidad = resolverEntidad(gestionPreferida, f.getEntidadCodigo());
                if (entidad == null) {
                    sinEntidad++;
                    continue;
                }

                Predio predio = predioServicio
                    .findByEntidadAndUnidadIgnoreCase(entidad, normUnidad(f.getUnidad()))
                    .orElse(null);
                if (predio == null) {
                    sinPredio++;
                    continue;
                }

                String clave = predio.getIdPredio() + "-" + f.getCodOfi();
                Oficina oficinaExistente = oficinasExistentes.get(clave);
                
                Oficina oficina;
                boolean esNueva = (oficinaExistente == null);
                
                if (esNueva) {
                    oficina = new Oficina();
                    oficina.setPredio(predio);
                    oficina.setCodOfi(f.getCodOfi());
                } else {
                    oficina = oficinaExistente;
                }

                String nombreFinal = (f.getNomOfic() != null && !f.getNomOfic().isBlank())
                        ? f.getNomOfic().trim()
                        : ("OFICINA " + f.getCodOfi());
                if (nombreFinal.length() > 255) {
                    nombreFinal = nombreFinal.substring(0, 255);
                }

                String observ = f.getObserv();
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) {
                    observ = null;
                }

                oficina.setNombre(nombreFinal);
                oficina.setObserv(observ);
                oficina.setFechaUlt(f.getFeult());
                oficina.setUsuario(f.getUsuario() == null 
                    ? null 
                    : (f.getUsuario().length() > 60 
                        ? f.getUsuario().substring(0, 60) 
                        : f.getUsuario()));
                oficina.setApiEstado(f.getApiEstado());
                oficina.setEstado("ACTIVO");

                String nuevoHash = oficina.calcularHash();
                
                if (!esNueva && !forzarCompleto) {
                    if (nuevoHash.equals(oficina.getHashDatos())) {
                        skipped++;
                        continue;
                    }
                }

                oficina.setHashDatos(nuevoHash);
                oficina.setFechaUltimaSync(LocalDateTime.now());

                batch.add(oficina);
                if (esNueva) inserted++; else updated++;

                if (batch.size() >= 500) {
                    oficinaService.saveAll(batch);
                    batch.clear();
                }
            }
            
            if (!batch.isEmpty()) {
                oficinaService.saveAll(batch);
                batch.clear();
            }

            long duracion = System.currentTimeMillis() - inicio;
            syncControlService.registrarSincronizacion("oficina", filas.size(), inserted, updated, duracion);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalLeidas", filas.size(),
                "insertados", inserted,
                "actualizados", updated,
                "omitidos", skipped,
                "sinEntidad", sinEntidad,
                "sinPredio", sinPredio,
                "duracionMs", duracion,
                "mensaje", String.format("Sincronización completada en %.2f segundos", duracion / 1000.0)
            ));
            
        } catch (Exception ex) {

            syncControlService.registrarError("oficina", ex.getMessage());
            
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando OFICINA: " + ex.getMessage()
            ));
        }
    }

    private Map<String, Oficina> cargarOficinasEnCache(Short gestion) {
        List<Oficina> todas;
        
        if (gestion != null) {
            todas = oficinaService.findAll().stream()
                .filter(o -> o.getPredio() != null && 
                           o.getPredio().getEntidad() != null &&
                           gestion.equals(o.getPredio().getEntidad().getGestion()))
                .collect(Collectors.toList());
        } else {
            todas = oficinaService.findAll();
        }
        
        return todas.stream()
            .collect(Collectors.toMap(
                o -> o.getPredio().getIdPredio() + "-" + o.getCodOfi(),
                o -> o,
                (existing, replacement) -> existing
            ));
    }

    @GetMapping("/sync-info")
    @ResponseBody
    public ResponseEntity<?> obtenerInfoSync() {
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("oficina");
            
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

    private Entidad resolverEntidad(Short gestionPreferida, String codigo) {
        String cod = codigo.trim();
        String codNoZeros = stripLeftZeros(codigo);
        String codPad4 = leftPad4(codigo);

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

    private String stripLeftZeros(String s) {
        if (s == null)
            return null;
        String out = s.replaceFirst("^0+", "");
        return out.isEmpty() ? "0" : out;
    }

    private String leftPad4(String s) {
        String base = stripLeftZeros(s);
        try {
            return String.format("%04d", Integer.parseInt(base));
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private String normUnidad(String u) {
        return u == null ? null : u.trim();
    }
}