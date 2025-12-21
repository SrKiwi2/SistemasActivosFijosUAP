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

    private boolean esCiValido(String ci) {
        if (ci == null || ci.isBlank()) {
            return false;
        }
        
        // Limpiar espacios y puntos
        String ciLimpio = ci.trim().replaceAll("[.\\-\\s]", "");
        
        // Debe tener solo números y al menos 5 dígitos
        return ciLimpio.matches("\\d{5,}");
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
            
            // 2️⃣ Cargar caché
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

                // ✅ Validar campos obligatorios
                if (f.getEntidadCodigo() == null || f.getUnidad() == null || f.getCodOfi() == null) {
                    camposNulos++;
                    continue;
                }

                // 3️⃣ Detectar duplicados en el DBF (CORREGIDO)
                // ⚠️ AHORA INCLUIMOS EL CODRESP PARA DIFERENCIAR REGISTROS EN LA MISMA OFICINA
                String keyDbf = f.getEntidadCodigo() + "|" + 
                                f.getUnidad() + "|" + 
                                f.getCodOfi() + "|" + 
                                (f.getCodResp() != null ? f.getCodResp() : "NULL");

                if (!seenKeys.add(keyDbf)) {
                    duplicadosDbf++;
                    // Solo logueamos si es un duplicado real exacto
                    log.debug("⚠️ Duplicado REAL detectado en DBF: {}", keyDbf);
                    continue;
                }

                // 4️⃣ Resolver Oficina
                // La clave de oficina sigue siendo solo por ubicación (sin codResp)
                String keyOficina = f.getEntidadCodigo() + "|" + f.getUnidad() + "|" + f.getCodOfi();
                Oficina oficina = oficinasCache.get(keyOficina);
                
                if (oficina == null) {
                    sinOficina++;
                    registrosSinOficina.add("OFICINA NO ENCONTRADA: " + keyOficina + " - " + f.getNombre());
                    continue;
                }

                // 5️⃣ Resolver Persona (LÓGICA MEJORADA)
                Persona persona = null;
                boolean tieneCiValido = esCiValido(f.getCi());

                if (tieneCiValido) {
                    String ciNorm = f.getCi().trim().replaceAll("[.\\-\\s]", "").toUpperCase();
                    persona = personasCache.get(ciNorm);

                    if (persona == null) {
                        // CREAR NUEVA PERSONA CON CI
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
                        // ♻️ REPARACIÓN AUTOMÁTICA DE NOMBRES "SIN DATOS"
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
                            personasCache.put(ciNorm, persona); // Actualizar caché
                        }
                    }
                } else if (f.getNombre() != null && !f.getNombre().isBlank()) {
                    // Lógica por Nombre (cuando no hay CI)
                    String[] partes = procesarNombreCompleto(f.getNombre());
                    String nombreCompletoNorm = String.join(" ", partes[0], nvl(partes[1]), nvl(partes[2])).trim();
                    
                    persona = personasCache.get("NOMBRE:" + nombreCompletoNorm);
                    
                    if (persona == null) {
                         // Buscar en BD
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
                    // Fallback SIN DATOS
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

                if (persona == null) continue; // Safety check

                // 6️⃣ Cargo
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

                // 7️⃣ CLAVE ÚNICA DE RESPONSABLE
                // Esta clave debe coincidir con tu @UniqueConstraint de la Entidad
                // id_oficina + codigo_funcionario
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
                    // IMPORTANTE: Aquí asignamos la persona. 
                    // Si ya existe la persona (RIMBERT), se reutiliza su ID.
                    responsable.setPersona(persona); 
                    responsable.setCargo(cargo);
                    responsablesCache.put(claveResponsable, responsable);
                }

                // 9️⃣ Mapeo de datos
                responsable.setCodigoFuncionario(f.getCodResp() != null ? f.getCodResp().trim() : null);
                responsable.setPersona(persona); // Aseguramos actualización si la persona cambió (ej. reparación de SIN DATOS)
                responsable.setCargo(cargo);     // Aseguramos actualización de cargo
                
                String observ = f.getObserv();
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) observ = null;
                responsable.setObserv(observ);
                
                responsable.setFechaUlt(f.getFechaUlt());
                responsable.setUsuario(f.getUsuario());
                responsable.setCodExp(f.getCodExp());
                responsable.setApiEstado(f.getApiEstado());
                responsable.setEstado("ACTIVO");

                // 🔟 Hash Check
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

            // Registro Final
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
            
            // Retorno JSON
            Map<String, Object> response = resultado.toResponseMap();
            response.put("personasCreadas", personasCreadas);
            response.put("duplicadosDbf", duplicadosDbf);
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("Error sync", ex);
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
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
                String ciNormalizado = p.getCi().trim()
                .replaceAll("[.\\-\\s]", "")
                .toUpperCase();
            
                // Solo agregar si es numérico válido
                if (ciNormalizado.matches("\\d{5,}")) {
                    cache.put(ciNormalizado, p);
                }
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

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
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

    // =========================================================================
    // ✅ NUEVOS HELPERS OPTIMIZADOS (Pega esto al final de tu Controller)
    // =========================================================================

    /**
     * Procesa un nombre completo y devuelve un arreglo de 3 posiciones:
     * [0] = Nombre(s)
     * [1] = Apellido Paterno
     * [2] = Apellido Materno
     */
    private String[] procesarNombreCompleto(String nombreCompleto) {
        // 1. Limpieza centralizada
        String limpio = limpiarNombre(nombreCompleto);
        
        // Si está vacío, retornamos todo null
        if (limpio.isEmpty()) {
            return new String[]{null, null, null};
        }

        String[] partes = limpio.split("\\s+");
        int n = partes.length;

        String nombre;
        String paterno = null;
        String materno = null;

        if (n == 1) {
            // Caso: "JUAN" -> Solo nombre
            nombre = partes[0];
        } 
        else if (n == 2) {
            // Caso: "JUAN PEREZ" -> Nombre y Paterno
            nombre = partes[0];
            paterno = partes[1];
        } 
        else {
            // Caso 3+: "JUAN CARLOS PEREZ" o "JOHANES JOAQUIN OLIVEIRA MORENO"
            // Lógica: Las últimas 2 palabras son siempre Paterno y Materno.
            // Todo lo anterior es el Nombre.
            
            materno = partes[n - 1]; // Última palabra (MORENO)
            paterno = partes[n - 2]; // Penúltima palabra (OLIVEIRA)
            
            // Unir el resto para el nombre (JOHANES JOAQUIN)
            // Usamos Arrays.copyOfRange para tomar desde el inicio hasta antes del paterno
            nombre = String.join(" ", java.util.Arrays.copyOfRange(partes, 0, n - 2));
        }

        return new String[]{nombre, paterno, materno};
    }

    private String limpiarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "";
        }
        
        // Optimizamos la limpieza encadenando los replace
        String limpio = nombre.toUpperCase().trim();
        
        limpio = limpio
            .replace("Á", "A")
            .replace("É", "E")
            .replace("Í", "I")
            .replace("Ó", "O")
            .replace("Ú", "U")
            .replace("Ñ", "N") // Mantenemos tu lógica de quitar la Ñ
            .replace("-", " ")
            .replace(".", " ");

        // Eliminar todo lo que no sea letra o número
        limpio = limpio.replaceAll("[^A-Z0-9\\s]", "");
        
        // Eliminar espacios dobles
        return limpio.replaceAll("\\s+", " ").trim();
    }
}
