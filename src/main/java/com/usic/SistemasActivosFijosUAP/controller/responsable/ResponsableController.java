package com.usic.SistemasActivosFijosUAP.controller.responsable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.RespDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ResponsableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SyncResult;
import com.usic.SistemasActivosFijosUAP.model.dto.responsable.ResponsableApiDataDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesResponsableRepo;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/responsable")
@RequiredArgsConstructor
public class ResponsableController {

    private final IResponsableService responsableService;
    private final IPersonaService personaService;
    private final IOficinaService oficinaService;
    private final ICargoService cargoService;
    private final FuncionesResponsableRepo funcionesResponsableRepo;
    private final JavaDbfService dbfService;
    private final RespDbfWriterService respDbfWriterService;
    private final SyncControlService syncControlService;

    private static final Logger log = LoggerFactory.getLogger(ResponsableController.class);

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioResponsable(Model model) {
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        return "responsable/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping(value = "/api/datatables", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional(readOnly = true)
    public Map<String, Object> apiDataTables(
            @RequestParam(name = "draw", defaultValue = "1") int draw,
            @RequestParam(name = "start", defaultValue = "0") int start,
            @RequestParam(name = "length", defaultValue = "25") int length,
            @RequestParam(name = "search[value]", required = false) String search,
            @RequestParam(name = "oficinaId", required = false) Long oficinaId) {

        int size = (length < 0) ? 1000 : length;
        int page = Math.max(start, 0) / Math.max(size, 1);
        Pageable pageable = PageRequest.of(page, size);

        // 1. Obtener datos de BD
        Page<IResposableDao.ResponsableRow> p = responsableService.datatable(search, oficinaId, pageable);

        // 2. Cargar claves del DBF para comparar
        Set<String> clavesDbf = new HashSet<>();
        try {
            // Leemos el DBF
            var filasDbf = dbfService.listarResponsableAll(null);
            for(var f : filasDbf) {
                // Clave: ENTIDAD|UNIDAD|CODOFI|CODRESP
                String k = generarClaveUnica(
                    f.getEntidadCodigo(), 
                    f.getUnidad(), 
                    f.getCodOfi(), 
                    (f.getCodResp() != null ? f.getCodResp() : "0")
                );
                clavesDbf.add(k);
            }
        } catch (Exception e) {
            log.error("Error leyendo RESP.DBF para comparación", e);
        }

        // 3. Armar respuesta
        List<Map<String, Object>> data = new ArrayList<>(p.getNumberOfElements());

        for (var row : p.getContent()) {
            String idEnc = "";
            try { idEnc = Encriptar.encrypt(String.valueOf(row.getIdResponsable())); } catch (Exception e) {}

            // ✅ Generar clave con los datos que AHORA SÍ TRAE EL DAO
            String claveBd = generarClaveUnica(
                row.getEntidadCodigo(), 
                row.getUnidadCodigo(), 
                row.getCodOfi(), 
                row.getCodFun() // El helper lo limpia si tiene letras
            );

            boolean enDbf = clavesDbf.contains(claveBd);

            Map<String, Object> m = new HashMap<>();
            m.put("idEnc", idEnc);
            m.put("codFun", nvl(row.getCodFun()));
            m.put("nombre", nvl(row.getNombre()));
            m.put("paterno", nvl(row.getPaterno()));
            m.put("materno", nvl(row.getMaterno()));
            m.put("ci", nvl(row.getCi()));
            m.put("oficina", nvl(row.getOficina()));
            m.put("cargo", nvl(row.getCargo()));
            m.put("idResponsable", row.getIdResponsable()); // ID plano para JS
            
            // ⚠️ Bandera correcta para el frontend
            m.put("existeEnDbf", enDbf); 

            data.add(m);
        }

        long total = responsableService.countActivos();
        Map<String, Object> res = new HashMap<>();
        res.put("draw", draw);
        res.put("recordsTotal", total);
        res.put("recordsFiltered", p.getTotalElements());
        res.put("data", data);
        res.put("source", "db");
        
        return res;
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario")
    public String formularioResponsable(Model model) { 
        model.addAttribute("responsable", new Responsable());
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        return "responsable/formulario";
    }

    @GetMapping("/obtener-siguiente-codigo-funcionario")
    @ResponseBody
    public Short obtenerSiguienteCodigoFuncionario(@RequestParam("idOficina") Long idOficina) {
        String codigoStr = funcionesResponsableRepo.siguienteCodigoPorOficinaStr(idOficina);
        try {
            return Short.parseShort(codigoStr); 
        } catch (Exception e) {
            return 1; 
        }
    }

    @GetMapping("/consultar-api-datos")
    @ResponseBody
    public ResponseEntity<ResponsableApiDataDTO> consultarApiDatos(
            @RequestParam String codigoFuncionario,
            @RequestParam String ci) {
        try {
            ResponsableApiDataDTO dto = responsableService.getResponsableDataFromApi(codigoFuncionario, ci);
            return ResponseEntity.ok(dto);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-responsable")
    @ResponseBody
    public ResponseEntity<?> registrarResponsable(
            HttpServletRequest request,
            @RequestParam(required = false) String codigoApi,
            @RequestParam String ci,
            @RequestParam String codigoFuncionario,
            @RequestParam Long idOficina,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String paterno,
            @RequestParam(required = false) String materno,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) String cargoApi) {

        log.info("=== INICIANDO REGISTRO DE RESPONSABLE ===");
        log.info("CI: {}, Código: {}, Nombre: {}, Paterno: {}, Materno: {}", 
            ci, codigoFuncionario, nombre, paterno, materno);
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
        
        try {
            
            Oficina oficina = oficinaService.findById(idOficina);
            if (oficina == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "msg", "No se encontró la oficina especificada"
                ));
            }
            
            Responsable respExistente = responsableService.findByCodigoFuncionarioYOficina(
                codigoFuncionario, idOficina
            );
            if (respExistente != null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "msg", String.format("Ya existe un responsable con código %s en la oficina %s", 
                                        codigoFuncionario, oficina.getNombre())
                ));
            }
            
            Persona persona = null;
            boolean personaNueva = false;
            
            if (ci != null && !ci.trim().isEmpty()) {
                persona = personaService.buscarPersonaPorCI(ci.trim());
                log.info("Búsqueda por CI '{}': {}", ci, (persona != null ? "Encontrada" : "No encontrada"));
            }
            
            if (persona == null) {
                boolean tieneDatosSuficientes = (nombre != null && !nombre.trim().isEmpty()) && 
                                            (paterno != null && !paterno.trim().isEmpty());
                
                if (tieneDatosSuficientes) {
                    log.info("Buscando por nombre: {} {} {}", nombre, paterno, materno);
                    
                    try {

                        persona = personaService.buscarPersonaPorNombreCompletoUno(
                            nombre.trim(), 
                            paterno.trim(), 
                            (materno != null && !materno.trim().isEmpty()) ? materno.trim() : null
                        );
                        
                        log.info("Búsqueda exacta por nombre: {}", (persona != null ? "Encontrada" : "No encontrada"));
                        
                        if (persona == null) {
                            List<Persona> personasCoincidentes = personaService.buscarPorNombreApellidos(
                                nombre.trim(), 
                                paterno.trim(), 
                                (materno != null && !materno.trim().isEmpty()) ? materno.trim() : null
                            );
                            
                            if (personasCoincidentes != null && !personasCoincidentes.isEmpty()) {
                                log.info("Se encontraron {} personas coincidentes", personasCoincidentes.size());
                                
                                StringBuilder msg = new StringBuilder("Se encontraron personas similares:\n");
                                for (Persona p : personasCoincidentes) {
                                    msg.append(String.format("- %s (CI: %s)\n", 
                                        p.getNombreCompleto(), 
                                        p.getCi() != null ? p.getCi() : "Sin CI"
                                    ));
                                }
                                msg.append("\n¿Desea continuar creando una nueva persona?");
                                
                                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                                    "ok", false,
                                    "msg", msg.toString(),
                                    "personasCoincidentes", personasCoincidentes.stream()
                                        .limit(10)
                                        .map(p -> Map.of(
                                            "idPersona", p.getIdPersona(),
                                            "nombreCompleto", p.getNombreCompleto(),
                                            "ci", p.getCi() != null ? p.getCi() : "",
                                            "correo", p.getCorreo() != null ? p.getCorreo() : ""
                                        ))
                                        .collect(Collectors.toList()),
                                    "requireConfirmacion", true
                                ));
                            }
                        }
                    } catch (Exception e) {
                        log.error("Error en búsqueda por nombre: {}", e.getMessage());
                    }
                } else {
                    log.warn("No se proporcionaron datos suficientes para buscar por nombre (nombre y paterno requeridos)");
                }
            }
            
            if (persona != null) {
                boolean yaEsResponsableEnOficina = responsableService.existeResponsablePorPersonaYOficina(
                    persona.getIdPersona(), idOficina
                );
                
                if (yaEsResponsableEnOficina) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "ok", false,
                        "msg", String.format("La persona %s ya es responsable en la oficina %s", 
                                            persona.getNombreCompleto(), oficina.getNombre())
                    ));
                }
            }

            if (persona == null) {
                personaNueva = true;
                persona = new Persona();
                persona.setCi(ci != null && !ci.trim().isEmpty() ? ci.trim() : null);
                persona.setNombre(nombre != null && !nombre.trim().isEmpty() ? nombre.trim().toUpperCase() : null);
                persona.setPaterno(paterno != null && !paterno.trim().isEmpty() ? paterno.trim().toUpperCase() : null);
                persona.setMaterno(materno != null && !materno.trim().isEmpty() ? materno.trim().toUpperCase() : null);
                persona.setCorreo(correo != null && !correo.trim().isEmpty() ? correo.trim() : null);
                persona.setEstado("ACTIVO");
                
                if (usuario != null) {
                    persona.setRegistroIdUsuario(usuario.getIdUsuario());
                }
                
                personaService.save(persona);
                log.info("✅ Nueva persona creada: {} (ID: {})", persona.getNombreCompleto(), persona.getIdPersona());
            } else {
                log.info("✅ Persona existente encontrada: {} (ID: {})", persona.getNombreCompleto(), persona.getIdPersona());
            }

            Responsable responsable = new Responsable();
            responsable.setCodigoApi(codigoApi);
            responsable.setCodigoFuncionario(codigoFuncionario.trim());
            responsable.setPersona(persona);
            responsable.setOficina(oficina);

            if (cargoApi != null && !cargoApi.trim().isEmpty()) {
                Cargo cargoEncontrado = cargoService.buscarPorNombre(cargoApi.trim());
                if (cargoEncontrado == null) {
                    Cargo nuevoCargo = new Cargo();
                    nuevoCargo.setNombre(cargoApi.trim().toUpperCase());
                    nuevoCargo.setDescripcion("Cargo proporcionado de la API");
                    nuevoCargo.setEstado("ACTIVO");
                    nuevoCargo.setRegistro(new Date());
                    if (usuario != null) {
                        nuevoCargo.setRegistroIdUsuario(usuario.getIdUsuario());
                    }
                    cargoService.save(nuevoCargo);
                    responsable.setCargo(nuevoCargo);
                    log.info("✅ Nuevo cargo creado: {}", nuevoCargo.getNombre());
                } else {
                    responsable.setCargo(cargoEncontrado);
                    log.info("✅ Cargo existente asignado: {}", cargoEncontrado.getNombre());
                }
            }
            
            responsable.setFechaUlt(LocalDate.now());
            responsable.setUsuario(usuarioNombre);
            responsable.setApiEstado(Short.valueOf("1"));
            responsable.setCodExp(Short.valueOf("1"));
            responsable.setEstado("ACTIVO");
            
            if (usuario != null) {
                responsable.setRegistroIdUsuario(usuario.getIdUsuario());
            }
            
            responsableService.save(responsable);
            log.info("Responsable creado en PostgreSQL: ID={}, Código={}", 
                    responsable.getIdResponsable(), responsable.getCodigoFuncionario());

            Responsable responsableCargado = responsableService.findByIdWithRelations(responsable.getIdResponsable());

            try {
                Predio predio = oficina.getPredio();
                Entidad entidad = (predio != null) ? predio.getEntidad() : null;
                
                if (predio == null || entidad == null) {
                    log.warn("Responsable {} sin predio/entidad completo, no se sincroniza con DBF", 
                            responsable.getIdResponsable());
                    return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "msg", "Registrado en PostgreSQL, pero SIN datos de Predio/Entidad para DBF",
                        "id", responsable.getIdResponsable(),
                        "personaNueva", personaNueva
                    ));
                }
                
                String entidadCode = entidad.getEntidadCodigo();
                String unidadCode = predio.getUnidad();
                
                Integer codResp = null;
                if (codigoFuncionario != null) {
                    String onlyDigits = codigoFuncionario.replaceAll("\\D+", "");
                    if (!onlyDigits.isEmpty()) {
                        codResp = Integer.valueOf(onlyDigits);
                    }
                }

                if (codResp != null && respDbfWriterService.existsByCodResp(
                        codResp, oficina.getCodOfi(), entidadCode, unidadCode)) {
                    log.warn("Responsable CODRESP={} ya existe en RESP.DBF", codResp);
                    return ResponseEntity.ok(Map.of(
                        "ok", true,
                        "msg", "Registrado en PostgreSQL, pero ya existía en DBF",
                        "id", responsable.getIdResponsable(),
                        "personaNueva", personaNueva
                    ));
                }
                
                respDbfWriterService.insertarDesdeResponsable(
                    responsableCargado, entidadCode, unidadCode, usuarioNombre
                );
                
                log.info("Responsable {} registrado exitosamente en PostgreSQL y DBF", 
                        responsable.getIdResponsable());
                
            } catch (Exception e) {
                log.error("Error insertando responsable en DBF: {}", e.getMessage(), e);
                return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "msg", "Se guardó en PostgreSQL pero falló el registro en DBF: " + e.getMessage(),
                    "id", responsable.getIdResponsable()
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "msg", String.format("Responsable registrado correctamente%s", 
                                    personaNueva ? " (nueva persona creada)" : ""),
                "id", responsable.getIdResponsable(),
                "personaNueva", personaNueva
            ));
            
        } catch (Exception e) {
            log.error("Error registrando responsable: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "ok", false,
                "msg", "Error al registrar: " + e.getMessage()
            ));
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-responsable-forzado")
    @ResponseBody
    public ResponseEntity<?> registrarResponsableForzado(
            HttpServletRequest request,
            @RequestParam(required = false) String codigoApi,
            @RequestParam String ci,
            @RequestParam String codigoFuncionario,
            @RequestParam Long idOficina,
            @RequestParam String nombre,
            @RequestParam String paterno,
            @RequestParam(required = false) String materno,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) Long idCargo,
            @RequestParam(required = false) String nombreCargoApi,
            @RequestParam(defaultValue = "false") boolean forzarCreacion) {
        
        return registrarResponsable(request, codigoApi, ci, codigoFuncionario, 
                                   idOficina, nombre, paterno, materno, correo, nombreCargoApi);
    }

    private boolean esCiValido(String ci) {
        if (ci == null || ci.isBlank()) {
            return false;
        }
        
        String ciLimpio = ci.trim().replaceAll("[.\\-\\s]", "");
        return ciLimpio.matches("\\d{5,}");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {
        
        long inicio = System.currentTimeMillis();
        
        try {
            var filas = dbfService.listarResponsableAll(q);
            log.info("✅ Total registros leídos del DBF: {}", filas.size());
            
            Map<String, Oficina> oficinasCache = cargarOficinasEnCache();
            Map<String, Persona> personasCache = cargarPersonasEnCache();
            Map<String, Cargo> cargosCache = cargarCargosEnCache();
            Map<String, Responsable> responsablesCache = cargarResponsablesEnCache();
            
            int inserted = 0, updated = 0, skipped = 0;
            int sinOficina = 0, personasCreadas = 0, cargosCreados = 0;
            int camposNulos = 0;
            int duplicadosDbf = 0;
            int sinCi = 0;
            int colisionesCache = 0;

            List<Responsable> batch = new ArrayList<>(500);
            Set<String> seenKeys = new HashSet<>(filas.size());

            List<String> registrosSinOficina = new ArrayList<>();
            List<String> registrosSinCi = new ArrayList<>();
            Map<String, Integer> colisionesPorClave = new HashMap<>();

            for (var f : filas) {

                if (f.getEntidadCodigo() == null || f.getUnidad() == null || f.getCodOfi() == null) {
                    camposNulos++;
                    continue;
                }

                String keyDbf = f.getEntidadCodigo() + "|" + 
                                f.getUnidad() + "|" + 
                                f.getCodOfi() + "|" + 
                                (f.getCodResp() != null ? f.getCodResp() : "NULL");

                if (!seenKeys.add(keyDbf)) {
                    duplicadosDbf++;
                    log.debug("⚠️ Duplicado REAL detectado en DBF: {}", keyDbf);
                    continue;
                }

                String keyOficina = f.getEntidadCodigo() + "|" + f.getUnidad() + "|" + f.getCodOfi();
                Oficina oficina = oficinasCache.get(keyOficina);
                
                if (oficina == null) {
                    sinOficina++;
                    registrosSinOficina.add("OFICINA NO ENCONTRADA: " + keyOficina + " - " + f.getNombre());
                    continue;
                }

                Persona persona = null;
                boolean tieneCiValido = esCiValido(f.getCi());

                if (tieneCiValido) {
                    String ciNorm = f.getCi().trim().replaceAll("[.\\-\\s]", "").toUpperCase();
                    persona = personasCache.get(ciNorm);

                    if (persona == null) {
                        String[] partes = procesarNombreCompleto(f.getNombre());
                        String nombre = partes[0] == null || partes[0].isEmpty() ? "SIN DATOS" : partes[0];
                        
                        persona = new Persona();
                        persona.setNombre(nombre);
                        persona.setPaterno(partes[1]);
                        persona.setMaterno(partes[2]);
                        persona.setCi(ciNorm);
                        persona.setEstado("ACTIVO");
                        persona = personaService.save(persona);

                        personasCache.put(ciNorm, persona);
                        personasCreadas++;
                    } else {
                        boolean nombreInvalido = "SIN DATOS".equals(persona.getNombre()) 
                                                || persona.getNombre() == null 
                                                || persona.getNombre().isBlank();

                        if (nombreInvalido && f.getNombre() != null && !f.getNombre().isBlank()) {
                            log.info("♻️ Reparando nombre Persona ID {}: '{}'", persona.getIdPersona(), f.getNombre());
                            String[] partes = procesarNombreCompleto(f.getNombre());
                            persona.setNombre(partes[0]);
                            persona.setPaterno(partes[1]);
                            persona.setMaterno(partes[2]);
                            persona = personaService.save(persona);
                            personasCache.put(ciNorm, persona);
                        }
                    }
                } else if (f.getNombre() != null && !f.getNombre().isBlank()) {
                    String[] partes = procesarNombreCompleto(f.getNombre());
                    String nombreCompletoNorm = String.join(" ", partes[0], nvl(partes[1]), nvl(partes[2])).trim();
                    
                    persona = personasCache.get("NOMBRE:" + nombreCompletoNorm);
                    
                    if (persona == null) {
                         persona = personaService.buscarPersonaPorNombreCompletoUno(partes[0], partes[1], partes[2]);
                         if (persona == null) {
                            persona = new Persona();
                            persona.setNombre(partes[0]);
                            persona.setPaterno(partes[1]);
                            persona.setMaterno(partes[2]);
                            persona.setEstado("ACTIVO");
                            persona = personaService.save(persona);
                            personasCreadas++;
                         }
                         personasCache.put("NOMBRE:" + nombreCompletoNorm, persona);
                    }
                } else {
                    persona = personasCache.get("NOMBRE:SIN DATOS");
                    if (persona == null) {
                        persona = personaService.buscarPersonaPorNombreCompletoUno("SIN DATOS", null, null);
                        if (persona == null) {
                             persona = new Persona();
                             persona.setNombre("SIN DATOS");
                             persona.setEstado("ACTIVO");
                             persona = personaService.save(persona);
                        }
                        personasCache.put("NOMBRE:SIN DATOS", persona);
                    }
                    sinCi++;
                }

                if (persona == null) continue;

                Cargo cargo = null;
                if (f.getCargo() != null && !f.getCargo().isBlank()) {
                    String keyCargo = f.getCargo().toUpperCase().trim();
                    cargo = cargosCache.get(keyCargo);
                    if (cargo == null) {
                        cargo = new Cargo();
                        cargo.setNombre(f.getCargo().trim());
                        cargo.setEstado("ACTIVO");
                        cargo = cargoService.save(cargo);
                        cargosCache.put(keyCargo, cargo);
                        cargosCreados++;
                    }
                }

                String claveResponsable = oficina.getIdOficina() + "|" + 
                    (f.getCodResp() != null ? f.getCodResp().trim() : "NULL");
                
                Responsable responsable = responsablesCache.get(claveResponsable);

                if (responsable != null) {
                    colisionesPorClave.merge(claveResponsable, 1, Integer::sum);
                }
                
                boolean esNuevo = (responsable == null);
                
                if (esNuevo) {
                    responsable = new Responsable();
                    responsable.setOficina(oficina);
                    responsable.setPersona(persona); 
                    responsable.setCargo(cargo);
                    responsablesCache.put(claveResponsable, responsable);
                }

                responsable.setCodigoFuncionario(f.getCodResp() != null ? f.getCodResp().trim() : null);
                responsable.setPersona(persona);
                responsable.setCargo(cargo);
                
                String observ = f.getObserv();
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) observ = null;
                responsable.setObserv(observ);
                
                responsable.setFechaUlt(f.getFechaUlt());
                responsable.setUsuario(f.getUsuario());
                responsable.setCodExp(f.getCodExp());
                responsable.setApiEstado(f.getApiEstado());
                responsable.setEstado("ACTIVO");

                String nuevoHash = responsable.calcularHash();
                if (!esNuevo && !forzarCompleto) {
                    if (nuevoHash.equals(responsable.getHashDatos())) {
                        skipped++;
                        continue;
                    }
                }

                responsable.setHashDatos(nuevoHash);
                responsable.setFechaUltimaSync(LocalDateTime.now());

                batch.add(responsable);
                if (esNuevo) inserted++; else updated++;

                if (batch.size() >= 500) {
                    responsableService.saveAll(batch);
                    batch.clear();
                }
            }
            
            if (!batch.isEmpty()) {
                responsableService.saveAll(batch);
                batch.clear();
            }

            long duracion = System.currentTimeMillis() - inicio;
            SyncResult resultado = SyncResult.builder()
                .totalLeidas(filas.size())
                .insertados(inserted)
                .actualizados(updated)
                .duracionMs(duracion)
                .omitidos(skipped)
                .sinOficina(sinOficina)
                .build();
            
            syncControlService.registrarSincronizacion("responsable", resultado);
            
            Map<String, Object> response = resultado.toResponseMap();
            response.put("personasCreadas", personasCreadas);
            response.put("duplicadosDbf", duplicadosDbf);
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("Error sync", ex);
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/personas/buscar-por-ci")
    @ResponseBody
    public ResponseEntity<?> buscarPersonaApi(@RequestParam String ci) {
        Persona p = personaService.buscarPersonaPorCI(ci);
        if (p != null) {
            return ResponseEntity.ok(Map.of(
                "nombre", p.getNombre(),
                "paterno", p.getPaterno() != null ? p.getPaterno() : "",
                "materno", p.getMaterno() != null ? p.getMaterno() : ""
            ));
        }
        return ResponseEntity.notFound().build();
    }

    // Endpoint para Select2 de Cargos
    @GetMapping("/api/cargos/search")
    @ResponseBody
    public List<Map<String, String>> buscarCargos(@RequestParam(required = false) String q) {
        // Implementa un buscarPorNombreLike en CargoService
        List<Cargo> cargos = (q == null || q.isBlank()) 
            ? cargoService.findAll() 
            : cargoService.buscarPorNombreLike("%" + q.toUpperCase() + "%");
            
        return cargos.stream()
            .limit(20) // Limitar resultados
            .map(c -> Map.of("nombre", c.getNombre()))
            .collect(Collectors.toList());
    }
    
    // Endpoint para recargar Oficinas en el Select (JSON ligero)
    @GetMapping("/api/oficinas/list-select")
    @ResponseBody
    public List<Map<String, Object>> listarOficinasSelect() {
        return oficinaService.listarOficinas().stream().map(o -> {
            Map<String, Object> m = new HashMap<>();
            m.put("idOficina", o.getIdOficina());
            m.put("codOfi", o.getCodOfi());
            m.put("nombre", o.getNombre());
            m.put("predio", Map.of("unidad", o.getPredio().getUnidad()));
            return m;
        }).collect(Collectors.toList());
    }

    private Map<String, Oficina> cargarOficinasEnCache() {
        List<Oficina> todas = oficinaService.findAll();
        
        Map<String, Oficina> cache = new HashMap<>();
        for (Oficina o : todas) {
            if (o.getPredio() != null && o.getPredio().getEntidad() != null) {
                String key = o.getPredio().getEntidad().getEntidadCodigo() + "|" +
                            o.getPredio().getUnidad() + "|" +
                            o.getCodOfi();
                cache.put(key, o);
            }
        }
        return cache;
    }

    private Map<String, Persona> cargarPersonasEnCache() {
        List<Persona> todas = personaService.findAll();
        Map<String, Persona> cache = new HashMap<>(todas.size() * 2);
        log.info("=== CONSTRUYENDO CACHÉ DE PERSONAS ===");
        
        for (Persona p : todas) {
            if (p.getCi() != null && !p.getCi().isBlank()) {
                String ciNormalizado = p.getCi().trim()
                .replaceAll("[.\\-\\s]", "")
                .toUpperCase();
            
                if (ciNormalizado.matches("\\d{5,}")) {
                    cache.put(ciNormalizado, p);
                }
            }
            
            String nombreCompleto = String.join(" ",
                p.getNombre() != null ? p.getNombre().trim() : "",
                p.getPaterno() != null ? p.getPaterno().trim() : "",
                p.getMaterno() != null ? p.getMaterno().trim() : ""
            ).trim().toUpperCase();
            
            if (!nombreCompleto.isEmpty()) {
                cache.put("NOMBRE:" + nombreCompleto, p);
            }
        }
        log.info("✅ Personas en caché: {} (con {} claves)", todas.size(), cache.size());
        return cache;
    }

    private Map<String, Cargo> cargarCargosEnCache() {
        List<Cargo> todos = cargoService.findAll();
        
        return todos.stream()
            .collect(Collectors.toMap(
                c -> (c.getNombre() != null ? c.getNombre().toUpperCase().trim() : ""),
                c -> c,
                (existing, replacement) -> existing,
                HashMap::new
            ));
    }

    private Map<String, Responsable> cargarResponsablesEnCache() {
        List<Responsable> todos = responsableService.findAll();
        Map<String, Responsable> cache = new HashMap<>(todos.size());
        log.info("=== CONSTRUYENDO CACHÉ DE RESPONSABLES ===");
        
        for (Responsable r : todos) {
            if (r.getOficina() != null) {
                String key = r.getOficina().getIdOficina() + "|" + 
                            (r.getCodigoFuncionario() != null ? r.getCodigoFuncionario() : "NULL");
                
                cache.put(key, r);
            }
        }
        
        log.info("✅ Responsables en caché: {}", cache.size());
        return cache;
    }

    @GetMapping("/sync-info")
    @ResponseBody
    public ResponseEntity<?> obtenerInfoSync() {
        try {
            SyncControl syncInfo = syncControlService.obtenerInfoSincronizacion("responsable");
            
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

    @ValidarUsuarioAutenticado
    @PostMapping("/subir-dbf/{id}")
    @ResponseBody
    public ResponseEntity<?> subirResponsableADbf(@PathVariable("id") String idEnc, HttpServletRequest request) {
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            Responsable resp = responsableService.findByIdWithRelations(id); // Asegurar traer oficina/predio
            
            if (resp == null) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No encontrado"));

            Oficina ofi = resp.getOficina();
            if (ofi == null || ofi.getPredio() == null || ofi.getPredio().getEntidad() == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Datos de oficina incompletos"));
            }

            String entidad = ofi.getPredio().getEntidad().getEntidadCodigo();
            String unidad = ofi.getPredio().getUnidad(); // O getCodigo() según tu lógica normalizada
            String usuario = (Usuario) request.getSession().getAttribute("usuario") != null ? 
                             ((Usuario) request.getSession().getAttribute("usuario")).getUsuario() : "SISTEMA";

            Integer codResp = Integer.valueOf(resp.getCodigoFuncionario().replaceAll("\\D+", ""));

            // Verificar si existe (usando el servicio blindado)
            if (respDbfWriterService.existsByCodResp(codResp, ofi.getCodOfi(), entidad, unidad)) {
                // Actualizar
                respDbfWriterService.actualizarDesdeResponsable(
                    codResp, ofi.getCodOfi(), entidad, unidad, 
                    resp, entidad, unidad, usuario
                );
                return ResponseEntity.ok(Map.of("ok", true, "msg", "Responsable actualizado en DBF."));
            }

            // Insertar
            respDbfWriterService.insertarDesdeResponsable(resp, entidad, unidad, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "msg", "Responsable insertado en DBF."));

        } catch (Exception e) {
            log.error("Error subiendo responsable", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String generarClaveUnica(String entidad, String unidad, Short codOfi, String codResp) {
        // 1. Normalizar Texto (Entidad y Unidad)
        // Quitamos espacios, pasamos a mayúsculas y eliminamos caracteres ocultos
        String e = (entidad == null) ? "" : entidad.trim().toUpperCase().replaceAll("\\p{C}", "");
        String u = (unidad == null) ? "" : unidad.trim().toUpperCase().replaceAll("\\p{C}", "");
        
        // 2. Normalizar Código Oficina
        String cOfi = (codOfi == null) ? "0" : String.valueOf(codOfi);

        // 3. Normalizar Código Responsable (CRÍTICO)
        // El DBF puede tener "005", "5 ", "05". La BD suele tener "5".
        // Convertimos todo a entero y luego a string para asegurar "5" == "5".
        String cResp = "0";
        if (codResp != null) {
            // Eliminar todo lo que no sea número
            String soloNumeros = codResp.replaceAll("\\D+", ""); 
            if (!soloNumeros.isEmpty()) {
                try {
                    // "005" -> 5 -> "5"
                    cResp = String.valueOf(Integer.parseInt(soloNumeros));
                } catch (NumberFormatException ex) {
                    cResp = soloNumeros; // Fallback por si acaso
                }
            }
        }

        return e + "|" + u + "|" + cOfi + "|" + cResp;
    }

    private String[] procesarNombreCompleto(String nombreCompleto) {

        String limpio = limpiarNombre(nombreCompleto);
        
        if (limpio.isEmpty()) {
            return new String[]{null, null, null};
        }

        String[] partes = limpio.split("\\s+");
        int n = partes.length;
        String nombre;
        String paterno = null;
        String materno = null;

        if (n == 1) {
            nombre = partes[0];
        } 

        else if (n == 2) {
            nombre = partes[0];
            paterno = partes[1];
        } 

        else {
            materno = partes[n - 1];
            paterno = partes[n - 2];
            nombre = String.join(" ", java.util.Arrays.copyOfRange(partes, 0, n - 2));
        }

        return new String[]{nombre, paterno, materno};
    }

    private String limpiarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        
        String limpio = nombre.toUpperCase().trim();
        
        limpio = limpio
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")
            .replace("Ñ", "N")
            .replace("-", " ")
            .replace(".", " ");

        limpio = limpio.replaceAll("[^A-Z0-9\\s]", "");
        
        return limpio.replaceAll("\\s+", " ").trim();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }
}
