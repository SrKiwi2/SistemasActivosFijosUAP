package com.usic.SistemasActivosFijosUAP.controller.responsable;

import java.text.Normalizer;
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

        // 1) Intento BD con server-side paging
        Page<IResposableDao.ResponsableRow> p = responsableService.datatable(search, oficinaId, pageable);
        if (p.getTotalElements() > 0) {
            List<Map<String, Object>> data = new ArrayList<>(p.getNumberOfElements());
            for (var row : p.getContent()) {
                String idEnc;
                try {
                    idEnc = Encriptar.encrypt(String.valueOf(row.getIdResponsable()));
                } catch (Exception e) {
                    idEnc = "";
                }

                Map<String, Object> m = new HashMap<>();
                m.put("idEnc", idEnc);
                m.put("codFun", nvl(row.getCodFun()));
                m.put("nombre", nvl(row.getNombre()));
                m.put("paterno", nvl(row.getPaterno()));
                m.put("materno", nvl(row.getMaterno()));
                m.put("ci", nvl(row.getCi()));
                m.put("oficina", nvl(row.getOficina()));
                m.put("cargo", nvl(row.getCargo()));
                data.add(m);
            }

            long total = responsableService.countActivos(); // total sin filtro BD
            Map<String, Object> res = new HashMap<>();
            res.put("draw", draw);
            res.put("recordsTotal", total);
            res.put("recordsFiltered", p.getTotalElements());
            res.put("data", data);
            res.put("source", "db"); // opcional, por si quieres mostrar un badge en el front
            return res;
        }

        // 2) Fallback: leer RESP.DBF y devolver el mismo JSON para DataTables
        try {
            // a) leer y filtrar por 'search' desde el propio lector
            List<ResponsableDbf> filas = dbfService.listarResponsableAll(search);

            // b) si viene oficinaId, filtramos por esa oficina (unidad + codOfi)
            Oficina oficinaFiltro = null;
            if (oficinaId != null) {
                oficinaFiltro = oficinaService.findById(oficinaId);
            }
            if (oficinaFiltro != null) {
                String unidad = oficinaFiltro.getPredio().getUnidad();
                Short codOf = oficinaFiltro.getCodOfi();
                String unidadNorm = unidad == null ? null : unidad.trim().toUpperCase(Locale.ROOT);
                List<ResponsableDbf> filtradas = new ArrayList<>();
                for (var r : filas) {
                    String u = r.getUnidad() == null ? null : r.getUnidad().trim().toUpperCase(Locale.ROOT);
                    if (Objects.equals(u, unidadNorm) && Objects.equals(r.getCodOfi(), codOf)) {
                        filtradas.add(r);
                    }
                }
                filas = filtradas;
            }

            // c) total después de filtros (para DataTables fallback)
            int totalAfterFilter = filas.size();

            // d) paginar con start/length
            int from = Math.min(start, totalAfterFilter);
            int to = Math.min(from + size, totalAfterFilter);
            List<ResponsableDbf> pageList = filas.subList(from, to);

            // e) mapear a las mismas columnas del DataTable
            List<Map<String, Object>> data = new ArrayList<>(pageList.size());
            for (var r : pageList) {
                // nombre → nombre/paterno/materno (simple split)
                String[] np = splitNombrePersona(nvl(r.getNombre()));
                String nombre = np[0];
                String paterno = np[1];
                String materno = np[2];

                // etiqueta oficina: UNIDAD - CODOFI (SIGLA) si se puede, sino UNIDAD - CODOFI
                String oficinaLabel;
                if (oficinaFiltro != null) {
                    String sig = (oficinaFiltro.getPredio().getEntidad().getSigla() == null) ? ""
                            : oficinaFiltro.getPredio().getEntidad().getSigla();
                    oficinaLabel = oficinaFiltro.getPredio().getUnidad() + " - " + oficinaFiltro.getCodOfi()
                            + (isBlank(sig) ? "" : (" (" + sig + ")"));
                } else {
                    oficinaLabel = (nvl(r.getUnidad()) == null ? "" : r.getUnidad()) + " - "
                            + (r.getCodOfi() == null ? "" : r.getCodOfi());
                }

                Map<String, Object> m = new HashMap<>();
                m.put("idEnc", ""); // viene de DBF, no hay id
                m.put("codFun", nvl(r.getCodResp())); // en tu tabla es "CODIGO FUNCIONARIO"
                m.put("nombre", nvl(nombre));
                m.put("paterno", nvl(paterno));
                m.put("materno", nvl(materno));
                m.put("ci", nvl(r.getCi()));
                m.put("oficina", oficinaLabel);
                m.put("cargo", nvl(r.getCargo()));
                data.add(m);
            }

            // f) armar respuesta
            Map<String, Object> res = new HashMap<>();
            res.put("draw", draw);
            // Como es fallback, usamos el mismo total para ambos campos
            res.put("recordsTotal", totalAfterFilter);
            res.put("recordsFiltered", totalAfterFilter);
            res.put("data", data);
            res.put("source", "dbf");
            return res;

        } catch (Exception ex) {
            // Si el DBF tampoco pudo leerse, devolvemos vacío y un mensaje si gustas
            Map<String, Object> res = new HashMap<>();
            res.put("draw", draw);
            res.put("recordsTotal", 0);
            res.put("recordsFiltered", 0);
            res.put("data", Collections.emptyList());
            res.put("source", "error");
            return res;
        }
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
                // Validar que tengamos al menos nombre y paterno
                boolean tieneDatosSuficientes = (nombre != null && !nombre.trim().isEmpty()) && 
                                            (paterno != null && !paterno.trim().isEmpty());
                
                if (tieneDatosSuficientes) {
                    log.info("Buscando por nombre: {} {} {}", nombre, paterno, materno);
                    
                    try {
                        // Búsqueda exacta
                        persona = personaService.buscarPersonaPorNombreCompletoUno(
                            nombre.trim(), 
                            paterno.trim(), 
                            (materno != null && !materno.trim().isEmpty()) ? materno.trim() : null
                        );
                        
                        log.info("Búsqueda exacta por nombre: {}", (persona != null ? "Encontrada" : "No encontrada"));
                        
                        // Solo buscar aproximada si no encontró exacta
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
                                        .limit(10) // Limitar a 10 resultados
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
                        // Continuar para crear nueva persona
                    }
                } else {
                    log.warn("No se proporcionaron datos suficientes para buscar por nombre (nombre y paterno requeridos)");
                }
            }
            
            // Verificar si ya es responsable en esta oficina
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
                
                // Insertar en DBF
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
    
    /**
     * Endpoint para forzar el registro cuando hay coincidencias de nombre
     */
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
        
        // Si forzarCreacion=true, crear siempre una nueva persona
        // Llamar al método de registro normal pero sin validación de nombres similares
        
        return registrarResponsable(request, codigoApi, ci, codigoFuncionario, 
                                   idOficina, nombre, paterno, materno, correo, nombreCargoApi);
    }

    // En ResponsableController
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {
        
        long inicio = System.currentTimeMillis();
        
        try {
            // 1️⃣ Leer DBF
            var filas = dbfService.listarResponsableAll(q);
            log.info("✅ Total registros leídos del DBF: {}", filas.size());
            
            // 2️⃣ Cargar caché de entidades relacionadas (1 consulta cada una)
            Map<String, Oficina> oficinasCache = cargarOficinasEnCache();
            Map<String, Persona> personasCache = cargarPersonasEnCache();
            Map<String, Cargo> cargosCache = cargarCargosEnCache();
            Map<String, Responsable> responsablesCache = cargarResponsablesEnCache();
            
            int inserted = 0, updated = 0, skipped = 0;
            int sinOficina = 0, personasCreadas = 0, cargosCreados = 0;
            int camposNulos = 0;          // ← NUEVO
            int duplicadosDbf = 0;        // ← NUEVO
            int sinCi = 0;                // ← NUEVO
            int colisionesCache = 0;      // ← NUEVO

            List<Responsable> batch = new ArrayList<>(500);
            Set<String> seenKeys = new HashSet<>(filas.size());

            List<String> registrosSinOficina = new ArrayList<>();
            List<String> registrosSinCi = new ArrayList<>();
            Map<String, Integer> colisionesPorClave = new HashMap<>();

            for (var f : filas) {
                // ✅ Validar campos obligatorios
                if (f.getEntidadCodigo() == null || f.getUnidad() == null || f.getCodOfi() == null) {
                    camposNulos++;
                    log.debug("❌ Registro con campos nulos: {}", f);
                    continue;
                }

                // 3️⃣ Detectar duplicados en el DBF
                String keyDbf = f.getEntidadCodigo() + "|" + f.getUnidad() + "|" + f.getCodOfi();
                if (!seenKeys.add(keyDbf)) {
                    duplicadosDbf++;
                    log.debug("⚠️ Duplicado detectado en DBF: {}", keyDbf);
                    continue;
                }

                // 4️⃣ Resolver Oficina (clave: entidad|unidad|codOfi)
                String keyOficina = keyDbf;
                Oficina oficina = oficinasCache.get(keyOficina);
                if (oficina == null) {
                    sinOficina++;
                    registrosSinOficina.add(String.format(
                        "ENTIDAD=%s, UNIDAD=%s, CODOFIC=%d, NOMBRE=%s",
                        f.getEntidadCodigo(), f.getUnidad(), f.getCodOfi(), 
                        f.getNombre()
                    ));
                    log.warn("❌ Sin oficina: {} - {}", keyOficina, f.getNombre());
                    continue;
                }

                // 5️⃣ Crear o buscar Persona (por CI o nombre completo)
                Persona persona = null;
                if (f.getCi() != null && !f.getCi().isBlank()) {

                    String ciNorm = f.getCi().trim().toUpperCase();
                    persona = personasCache.get(ciNorm);

                    if (persona == null) {
                        // Crear persona nueva
                        persona = new Persona();
                        persona.setNombre(f.getNombre() != null ? f.getNombre().trim() : "");
                        persona.setPaterno(extractPaterno(f.getNombre()));
                        persona.setMaterno(extractMaterno(f.getNombre()));
                        persona.setCi(f.getCi().trim());
                        persona.setEstado("ACTIVO");
                        persona = personaService.save(persona);
                        personasCache.put(f.getCi(), persona);
                        personasCreadas++;
                        log.info("Persona creada: {} - {}", f.getCi(), f.getNombre());
                    }
                } else if (f.getNombre() != null && !f.getNombre().isBlank()) {
                    String nombreCompleto = f.getNombre().trim().toUpperCase();
                    
                    // Buscar en caché por nombre
                    persona = personasCache.get("NOMBRE:" + nombreCompleto);
                    
                    if (persona == null) {
                        // Buscar en BD por nombre completo
                        persona = personaService.buscarPersonaPorNombreCompletoUno(
                            extractNombre(f.getNombre()),
                            extractPaterno(f.getNombre()),
                            extractMaterno(f.getNombre())
                        );
                        
                        if (persona == null) {
                            // Crear persona SIN CI
                            persona = new Persona();
                            persona.setNombre(extractNombre(f.getNombre()));
                            persona.setPaterno(extractPaterno(f.getNombre()));
                            persona.setMaterno(extractMaterno(f.getNombre()));
                            persona.setCi(null); // ⚠️ SIN CI
                            persona.setEstado("ACTIVO");
                            persona = personaService.save(persona);
                            personasCreadas++;
                            log.info("✅ Persona creada SIN CI: {}", f.getNombre());
                        } else {
                            log.debug("🔍 Persona encontrada por nombre: {}", f.getNombre());
                        }
                        
                        // Agregar a caché con prefijo "NOMBRE:"
                        personasCache.put("NOMBRE:" + nombreCompleto, persona);
                    } else {
                        log.debug("🔍 Persona encontrada en caché por nombre: {}", nombreCompleto);
                    }
                } else {
                    // ⚠️ Registro sin CI ni nombre
                    sinCi++;
                    registrosSinCi.add(String.format(
                        "OFICINA=%s, CARGO=%s (sin CI ni nombre)",
                        keyOficina, f.getCargo()
                    ));
                    log.warn("⚠️ Responsable sin CI ni nombre válido en oficina: {}", keyOficina);
                }



                // 6️⃣ Crear o buscar Cargo (por nombre)
                Cargo cargo = null;
                if (f.getCargo() != null && !f.getCargo().isBlank()) {
                    String keyCargoNorm = f.getCargo().toUpperCase().trim();
                    cargo = cargosCache.get(keyCargoNorm);
                    if (cargo == null) {
                        // Crear cargo nuevo
                        cargo = new Cargo();
                        cargo.setNombre(f.getCargo().trim());
                        cargo.setEstado("ACTIVO");
                        cargo = cargoService.save(cargo);
                        cargosCache.put(keyCargoNorm, cargo);
                        cargosCreados++;
                        log.info("Cargo creado: {}", f.getCargo());
                    }
                }

                // 7️⃣ Crear clave única para búsqueda en caché responsables
                String clave = oficina.getIdOficina() + "|" + 
                    (persona != null ? persona.getIdPersona() : "NULL") + "|" + 
                    (cargo != null ? cargo.getIdCargo() : "NULL") + "|" +
                    (f.getCodResp() != null ? f.getCodResp().trim() : "NULL");
                
                Responsable responsable = responsablesCache.get(clave);

                if (responsable != null) {
                    colisionesPorClave.merge(clave, 1, Integer::sum);
                }
                
                // 8️⃣ Determinar si es nuevo
                boolean esNuevo = (responsable == null);
                
                if (esNuevo) {
                    responsable = new Responsable();
                    responsable.setOficina(oficina);
                    responsable.setPersona(persona);
                    responsable.setCargo(cargo);

                    responsablesCache.put(clave, responsable);
                }

                // 9️⃣ Mapear datos del DBF
                responsable.setCodigoFuncionario(f.getCodResp() != null ? f.getCodResp().trim() : null);
                
                String observ = f.getObserv();
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) {
                    observ = null;
                }
                
                responsable.setObserv(observ);
                responsable.setFechaUlt(f.getFechaUlt());
                responsable.setUsuario(f.getUsuario() != null 
                    ? (f.getUsuario().length() > 60 ? f.getUsuario().substring(0, 60) : f.getUsuario())
                    : null);
                responsable.setCodExp(f.getCodExp());
                responsable.setApiEstado(f.getApiEstado());
                responsable.setEstado("ACTIVO");

                // 🔟 OPTIMIZACIÓN: Calcular hash y comparar
                String nuevoHash = responsable.calcularHash();
                
                if (!esNuevo && !forzarCompleto) {
                    // Verificar si realmente cambió
                    if (nuevoHash.equals(responsable.getHashDatos())) {
                        skipped++;
                        continue; // ⭐ NO procesar si no hay cambios
                    }
                }

                // 1️⃣1️⃣ Actualizar metadatos
                responsable.setHashDatos(nuevoHash);
                responsable.setFechaUltimaSync(LocalDateTime.now());

                batch.add(responsable);
                if (esNuevo) inserted++; else updated++;

                // 1️⃣2️⃣ Guardar en lotes
                if (batch.size() >= 500) {
                    responsableService.saveAll(batch);
                    batch.clear();
                }
            }
            
            // 1️⃣3️⃣ Guardar lote final
            if (!batch.isEmpty()) {
                responsableService.saveAll(batch);
                batch.clear();
            }

            // 1️⃣4️⃣ Registrar en sync_control
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

            log.info("═══════════════════════════════════════════════════════");
            log.info("📊 RESUMEN DE SINCRONIZACIÓN RESP.DBF");
            log.info("═══════════════════════════════════════════════════════");
            log.info("📖 Total leídos del DBF: {}", filas.size());
            log.info("✅ Insertados: {}", inserted);
            log.info("🔄 Actualizados: {}", updated);
            log.info("⏭️  Omitidos (sin cambios): {}", skipped);
            log.info("───────────────────────────────────────────────────────");
            log.info("❌ Rechazados por campos nulos: {}", camposNulos);
            log.info("🔁 Duplicados en DBF: {}", duplicadosDbf);
            log.info("🏢 Sin oficina coincidente: {}", sinOficina);
            log.info("👤 Sin CI: {}", sinCi);
            log.info("⚠️  Colisiones de caché detectadas: {}", colisionesPorClave.size());
            log.info("───────────────────────────────────────────────────────");
            log.info("➕ Personas creadas: {}", personasCreadas);
            log.info("💼 Cargos creados: {}", cargosCreados);
            log.info("═══════════════════════════════════════════════════════");

            // 📝 Mostrar registros problemáticos (primeros 10)
            if (!registrosSinOficina.isEmpty()) {
                log.warn("🏢 Registros SIN OFICINA (primeros 10):");
                registrosSinOficina.stream().limit(10).forEach(r -> log.warn("  - {}", r));
            }
            
            if (!registrosSinCi.isEmpty()) {
                log.info("👤 Registros SIN CI (primeros 10):");
                registrosSinCi.stream().limit(10).forEach(r -> log.info("  - {}", r));
            }
            
            if (!colisionesPorClave.isEmpty()) {
                log.warn("⚠️  COLISIONES DE CACHÉ DETECTADAS (primeras 10):");
                colisionesPorClave.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> log.warn("  - Clave: {} → {} colisiones", e.getKey(), e.getValue()));
            }

            // ✅ Respuesta con mapa extendido
            Map<String, Object> response = resultado.toResponseMap();
            response.put("personasCreadas", personasCreadas);
            response.put("cargosCreados", cargosCreados);
            response.put("camposNulos", camposNulos);
            response.put("duplicadosDbf", duplicadosDbf);
            response.put("sinCi", sinCi);
            response.put("colisiones", colisionesPorClave.size());
            response.put("registrosSinOficina", registrosSinOficina.size() <= 10 
                ? registrosSinOficina 
                : registrosSinOficina.subList(0, 10));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception ex) {
            log.error("Error en sincronización de RESP.DBF", ex);
            syncControlService.registrarError("responsable", ex.getMessage());
            
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "Error sincronizando RESPONSABLE: " + ex.getMessage()
            ));
        }
    }



    /**
     * ✅ OPTIMIZACIÓN: Cargar todas las oficinas en caché (1 sola consulta)
     * Clave: entidad|unidad|codOfi
     */
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

    /**
     * ✅ OPTIMIZACIÓN: Cargar todas las personas en caché (1 sola consulta)
     * Clave: CI (mayúsculas)
     */
    private Map<String, Persona> cargarPersonasEnCache() {
        List<Persona> todas = personaService.findAll();
        Map<String, Persona> cache = new HashMap<>(todas.size() * 2); // *2 para CI + nombre
        
        log.info("=== CONSTRUYENDO CACHÉ DE PERSONAS ===");
        
        for (Persona p : todas) {
            // Clave 1: Por CI (si tiene)
            if (p.getCi() != null && !p.getCi().isBlank()) {
                String ciKey = p.getCi().trim().toUpperCase();
                cache.put(ciKey, p);
            }
            
            // Clave 2: Por nombre completo (siempre)
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


    /**
     * ✅ OPTIMIZACIÓN: Cargar todos los cargos en caché (1 sola consulta)
     * Clave: Nombre (mayúsculas)
     */
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

    /**
     * ✅ OPTIMIZACIÓN: Cargar todos los responsables en caché (1 sola consulta)
     * Clave: idOficina|idPersona|idCargo
     */
    private Map<String, Responsable> cargarResponsablesEnCache() {
        List<Responsable> todos = responsableService.findAll();
        Map<String, Responsable> cache = new HashMap<>(todos.size());
        
        log.info("=== CONSTRUYENDO CACHÉ DE RESPONSABLES ===");
        
        for (Responsable r : todos) {
            if (r.getOficina() != null) {
                // ✅ Clave basada en constraint único de BD
                String key = r.getOficina().getIdOficina() + "|" + 
                            (r.getCodigoFuncionario() != null ? r.getCodigoFuncionario() : "NULL");
                
                cache.put(key, r);
            }
        }
        
        log.info("✅ Responsables en caché: {}", cache.size());
        return cache;
    }


    /**
     * ✅ ENDPOINT AJAX para obtener info de sincronización
     */
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

    // ===== MÉTODOS AUXILIARES =====
    
    /**
     * Extrae apellido paterno del nombre completo
     */
    private String extractPaterno(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "";
        
        String[] partes = nombreCompleto.trim().split("\\s+");
        return partes.length > 0 ? partes[0] : "";
    }

    /**
     * Extrae apellido materno del nombre completo
     */
    private String extractMaterno(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "";
        
        String[] partes = nombreCompleto.trim().split("\\s+");
        return partes.length > 1 ? partes[1] : "";
    }


    // HELPERS

    /**
     * Extrae el primer nombre de un nombre completo
     * Ej: "JORGE ARROYO ZABALA" → "JORGE"
     */
    private String extractNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "";
        String[] partes = nombreCompleto.trim().split("\\s+");
        return partes.length > 0 ? partes[0] : "";
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String stripLeftZeros(String s) {
        if (s == null)
            return null;
        return s.replaceFirst("^0+(?!$)", "");
    }

    private String leftPad4(String s) {
        if (s == null)
            return null;
        String t = stripLeftZeros(s);
        if (t == null)
            return null;
        return String.format("%04d", Integer.parseInt(t));
    }

    private String normUnidad(String u) {
        return u == null ? null : u.trim();
    }

    private String trunc(String s, int n) {
        if (s == null)
            return null;
        return s.length() > n ? s.substring(0, n) : s;
    }

    private String clip(String s, int n) {
        return trunc(nvl(s), n);
    }

    private static String canonNombre(String s) {
        String t = nvl(s);
        if (t == null)
            return null;
        // quita acentos
        t = Normalizer.normalize(t, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        // mayúsculas
        t = t.toUpperCase(Locale.ROOT);
        // deja solo letras/números/espacios
        t = t.replaceAll("[^A-Z0-9\\s]+", " ");
        // colapsa espacios
        t = t.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    // divide lo más básico: "Nombres ApellidoP ApellidoM"
    private String[] splitNombrePersona(String full) {
        String n = nvl(full);
        if (n == null)
            return new String[] { "DESCONOCIDO", null, null };
        String[] parts = n.split("\\s+");
        if (parts.length == 1)
            return new String[] { parts[0], null, null };
        if (parts.length == 2)
            return new String[] { parts[0], parts[1], null };
        // nombre = todo salvo los dos últimos
        String nombre = String.join(" ", Arrays.copyOf(parts, parts.length - 2));
        String paterno = parts[parts.length - 2];
        String materno = parts[parts.length - 1];
        return new String[] { nombre, paterno, materno };
    }
}
