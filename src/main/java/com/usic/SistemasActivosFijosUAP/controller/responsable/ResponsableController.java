package com.usic.SistemasActivosFijosUAP.controller.responsable;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ResponsableDbf;
import com.usic.SistemasActivosFijosUAP.model.dto.responsable.ResponsableApiDataDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesResponsableRepo;

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
    private final IEntidadService entidadService;
    private final RespDbfWriterService respDbfWriterService;

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
                // además, si quieres, podrías matchear ENTIDAD por sigla/código, pero con
                // unidad+codOf suele bastar
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
            res.put("source", "dbf"); // opcional: badge en front
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
            // Manejo de error de la API (ej: 500 Internal Server Error o 404 Not Found)
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
            @RequestParam(required = false) Long idCargo,
            @RequestParam(required = false) String nombreCargoApi) {
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
        
        try {
            // ========== VALIDACIÓN 1: Oficina existe ==========
            Oficina oficina = oficinaService.findById(idOficina);
            if (oficina == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "msg", "No se encontró la oficina especificada"
                ));
            }
            
            // ========== VALIDACIÓN 2: Código funcionario único en oficina ==========
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
            
            // ========== VALIDACIÓN 3: Buscar persona (CI primero, luego nombre) ==========
            Persona persona = null;
            boolean personaNueva = false;
            
            // Buscar por CI
            if (ci != null && !ci.trim().isEmpty()) {
                persona = personaService.buscarPersonaPorCI(ci.trim());
                log.info("Búsqueda por CI '{}': {}", ci, (persona != null ? "Encontrada" : "No encontrada"));
            }
            
            // Si no se encontró por CI, buscar por nombre completo
            if (persona == null && nombre != null && paterno != null) {
                log.info("Buscando por nombre: {} {} {}", nombre, paterno, materno);
                
                // Búsqueda exacta
                persona = personaService.buscarPersonaPorNombreCompletoUno(
                    nombre.trim(), 
                    paterno.trim(), 
                    (materno != null) ? materno.trim() : null
                );
                
                // Si no encontró exacta, buscar aproximada
                if (persona == null) {
                    List<Persona> personasCoincidentes = personaService.buscarPorNombreApellidos(
                        nombre.trim(), 
                        paterno.trim(), 
                        (materno != null) ? materno.trim() : null
                    );
                    
                    if (!personasCoincidentes.isEmpty()) {
                        // Si hay coincidencias, alertar al usuario
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
                
                log.info("Búsqueda por nombre: {}", (persona != null ? "Encontrada" : "No encontrada"));
            }
            
            // ========== VALIDACIÓN 4: Si la persona existe, verificar si ya es responsable ==========
            if (persona != null) {
                // Verificar si ya es responsable en ESTA oficina
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
                
                // Verificar si es responsable en otras oficinas (solo informativo)
                List<Responsable> responsablesExistentes = responsableService.findByPersonaId(
                    persona.getIdPersona()
                );
                
                if (!responsablesExistentes.isEmpty()) {
                    log.info("La persona {} ya es responsable en {} oficina(s)", 
                            persona.getNombreCompleto(), responsablesExistentes.size());
                }
            } else {
                // ========== Crear nueva persona ==========
                personaNueva = true;
                persona = new Persona();
                persona.setCi(ci != null ? ci.trim() : null);
                persona.setNombre(nombre != null ? nombre.trim() : null);
                persona.setPaterno(paterno != null ? paterno.trim() : null);
                persona.setMaterno(materno != null ? materno.trim() : null);
                persona.setCorreo(correo != null ? correo.trim() : null);
                persona.setEstado("ACTIVO");
                
                if (usuario != null) {
                    persona.setRegistroIdUsuario(usuario.getIdUsuario());
                }
                
                personaService.save(persona);
                log.info("Nueva persona creada: {} (ID: {})", persona.getNombreCompleto(), persona.getIdPersona());
            }

            // Cargar cargo si se proporcionó
            Cargo cargo = null;
            if (idCargo != null) {
                // Opción 1: Se seleccionó un cargo existente desde el front-end (o API lo encontró)
                cargo = cargoService.findById(idCargo);
                
                if (cargo == null) {
                    return ResponseEntity.badRequest().body(Map.of(
                        "ok", false,
                        "msg", "No se encontró el Cargo con ID: " + idCargo
                    ));
                }
            } 
            
            // Opción 2: Si NO tiene ID de cargo, PERO tiene nombre de cargo sugerido de la API, buscar/crear.
            else if (nombreCargoApi != null && !nombreCargoApi.trim().isEmpty()) {
                Long idUsuario = (usuario != null) ? usuario.getIdUsuario() : null;
                
                // LLAMADA CLAVE: Busca el cargo por nombre o lo crea si no existe
                cargo = cargoService.buscarOCrearPorNombre(nombreCargoApi, idUsuario);
                
                if (cargo != null) {
                    log.info("Cargo procesado: ID={} ({}).", cargo.getIdCargo(), cargo.getNombre());
                }
            }
            
            // ========== Crear Responsable ==========
            Responsable responsable = new Responsable();
            responsable.setCodigoApi(codigoApi); // Puede ser null
            responsable.setCodigoFuncionario(codigoFuncionario.trim());
            responsable.setPersona(persona);
            responsable.setOficina(oficina);
            responsable.setCargo(cargo);
            responsable.setFechaUlt(LocalDate.now());
            responsable.setUsuario(usuarioNombre);
            responsable.setApiEstado(Short.valueOf("1"));
            responsable.setCodExp(Short.valueOf("0"));
            responsable.setEstado("ACTIVO");
            
            if (usuario != null) {
                responsable.setRegistroIdUsuario(usuario.getIdUsuario());
            }
            
            // Guardar en PostgreSQL
            responsableService.save(responsable);
            log.info("Responsable creado en PostgreSQL: ID={}, Código={}", 
                    responsable.getIdResponsable(), responsable.getCodigoFuncionario());
            
            // ========== Insertar en RESP.DBF ==========
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
                
                // Convertir código funcionario a numérico para DBF
                Integer codResp = null;
                if (codigoFuncionario != null) {
                    String onlyDigits = codigoFuncionario.replaceAll("\\D+", "");
                    if (!onlyDigits.isEmpty()) {
                        codResp = Integer.valueOf(onlyDigits);
                    }
                }
                
                // Verificar si ya existe en DBF
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
                    responsable, entidadCode, unidadCode, usuarioNombre
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
                                   idOficina, nombre, paterno, materno, correo, idCargo, nombreCargoApi);
    }

    // En ResponsableController
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMounted(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) {
        try {
            var filas = dbfService.listarResponsableAll(q);

            int inserted = 0, updated = 0, sinEntidad = 0, sinPredio = 0, sinOficina = 0;
            int creadosPersona = 0, creadosCargo = 0, duplicadosEnDbf = 0;

            // caches para acelerar (mismo patrón que usaste)
            Map<String, Persona> cachePersonaPorCI = new HashMap<>(4000); // ci -> Persona
            Map<String, Persona> cachePersonaPorNombre = new HashMap<>(8000); // canon -> Persona
            Map<String, Cargo> cacheCargoPorNombre = new HashMap<>(2000);
            Set<String> antiDupDBF = new HashSet<>(filas.size());

            List<Responsable> batch = new ArrayList<>(500);

            for (var f : filas) {
                // ENTIDAD
                String cod = nvl(f.getEntidadCodigo());
                String codNoZeros = stripLeftZeros(cod);
                String codPad4 = leftPad4(cod);

                Entidad entidad = (gestionPreferida != null)
                        ? entidadService.findByGestionAndEntidadCodigo(gestionPreferida, cod)
                                .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codNoZeros))
                                .or(() -> entidadService.findByGestionAndEntidadCodigo(gestionPreferida, codPad4))
                                .orElse(null)
                        : entidadService.findTopByEntidadCodigoOrderByGestionDesc(cod)
                                .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codNoZeros))
                                .or(() -> entidadService.findTopByEntidadCodigoOrderByGestionDesc(codPad4))
                                .orElse(null);
                if (entidad == null) {
                    sinEntidad++;
                    continue;
                }

                // PREDIO (por unidad) y OFICINA (por codOfi)
                String unidad = normUnidad(f.getUnidad());
                Short codOfi = f.getCodOfi();
                if (isBlank(unidad) || codOfi == null) {
                    sinPredio++;
                    continue;
                }

                var oficina = oficinaService.findByEntidadUnidadAndCodOfi(entidad, unidad, codOfi).orElse(null);
                if (oficina == null) {
                    sinOficina++;
                    continue;
                }

                // Deduplicación por (oficinaId|codFunc|nombre|ci)
                String k = oficina.getIdOficina() + "|" + (f.getCodResp() == null ? "" : f.getCodResp().trim()) + "|" +
                        (f.getNombre() == null ? "" : f.getNombre().trim()) + "|"
                        + (f.getCi() == null ? "" : f.getCi().trim());
                if (!antiDupDBF.add(k)) {
                    duplicadosEnDbf++;
                    continue;
                }

                // PERSONA (upsert)
                Persona persona = null;
                String ci = nvl(f.getCi());
                if (!isBlank(ci)) {
                    persona = cachePersonaPorCI.get(ci);
                    if (persona == null) {
                        persona = personaService.findFirstByCi(ci).orElse(null);
                        if (persona != null)
                            cachePersonaPorCI.put(ci, persona);
                    }
                }
                if (persona == null) {
                    String canon = canonNombre(nvl(f.getNombre()));
                    if (!isBlank(canon)) {
                        persona = cachePersonaPorNombre.get(canon);
                    }
                    if (persona == null) {
                        // crear persona básica a partir del nombre “Nombres ApellidoP ApellidoM”
                        String[] np = splitNombrePersona(nvl(f.getNombre())); // implementa simple: [nombre, paterno,
                                                                              // materno]
                        persona = new Persona();
                        persona.setNombre(clip(np[0], 120));
                        persona.setPaterno(clip(np[1], 60));
                        persona.setMaterno(clip(np[2], 60));
                        persona.setCi(isBlank(ci) ? null : ci.trim());
                        persona.setEstado("ACTIVO");
                        personaService.save(persona);
                        creadosPersona++;
                        if (!isBlank(ci))
                            cachePersonaPorCI.put(ci, persona);
                        if (!isBlank(canon))
                            cachePersonaPorNombre.put(canon, persona);
                    }
                }

                // CARGO (upsert por nombre)
                Cargo cargo = null;
                String cargoNom = nvl(f.getCargo());
                if (!isBlank(cargoNom)) {
                    String keyCargo = cargoNom.trim().toLowerCase(Locale.ROOT);
                    cargo = cacheCargoPorNombre.get(keyCargo);
                    if (cargo == null) {
                        cargo = cargoService.findFirstByNombreIgnoreCase(cargoNom.trim()).orElse(null);
                        if (cargo == null) {
                            Cargo c = new Cargo();
                            c.setNombre(clip(cargoNom.trim(), 120));
                            c.setDescripcion(null);
                            c.setEstado("ACTIVO");
                            cargo = cargoService.save(c);
                            creadosCargo++;
                        }
                        cacheCargoPorNombre.put(keyCargo, cargo);
                    }
                }

                // UPSERT Responsable:
                // Regla: si viene CODRESP -> llave (oficina, codigoFuncionario)
                // si NO viene -> llave (oficina, persona)
                Responsable resp = null;
                String codFun = isBlank(f.getCodResp()) ? null : f.getCodResp().trim();

                if (!isBlank(codFun)) {
                    resp = responsableService.findByOficinaAndCodigoFuncionario(oficina, codFun).orElse(null);
                } else {
                    resp = responsableService.findByOficinaAndPersona(oficina, persona).orElse(null);
                }

                boolean nuevo = (resp == null);
                if (resp == null) {
                    resp = new Responsable();
                    resp.setOficina(oficina);
                    resp.setPersona(persona);
                }

                resp.setCodigoFuncionario(codFun);
                resp.setCargo(cargo);
                resp.setObserv(nvl(f.getObserv()));
                resp.setFechaUlt(f.getFechaUlt());
                resp.setUsuario(trunc(nvl(f.getUsuario()), 60));
                resp.setCodExp(f.getCodExp());
                resp.setApiEstado(f.getApiEstado());
                resp.setEstado("ACTIVO");

                if (nuevo)
                    inserted++;
                else
                    updated++;
                // Guarda por fila (para ver errores exactos) o en lote. Aquí guardo en lote
                // como en otros:
                batch.add(resp);
                if (batch.size() == 500) {
                    responsableService.saveAll(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty())
                responsableService.saveAll(batch);

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "totalLeidas", filas.size(),
                    "insertados", inserted,
                    "actualizados", updated,
                    "sinEntidad", sinEntidad,
                    "sinPredio", sinPredio,
                    "sinOficina", sinOficina,
                    "creadosPersona", creadosPersona,
                    "creadosCargo", creadosCargo,
                    "duplicadosEnDbf", duplicadosEnDbf));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "ok", false,
                    "message", "Error sincronizando RESPONSABLE: " + ex.getMessage()));
        }
    }

    
    // HELPERS

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
