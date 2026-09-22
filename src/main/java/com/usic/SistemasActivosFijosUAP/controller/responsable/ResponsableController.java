package com.usic.SistemasActivosFijosUAP.controller.responsable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SyncResult;
import com.usic.SistemasActivosFijosUAP.model.dto.responsable.ResponsableApiDataDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesResponsableRepo;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.AutorizacionService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService.DatosResponsable;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;

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
    private final SyncControlService syncControlService;
    private final ResponsableAltaService responsableAltaService;
    private final VsiafApoyoService vsiafApoyoService;
    private final IResposableDao responsableDao;
    private final ResponsableGestionService responsableGestionService;
    private final AutorizacionService autorizacionService;
    private final ActividadService actividadService;

    private static final Logger log = LoggerFactory.getLogger(ResponsableController.class);

    /**
     * Roles que pueden sincronizar a mano con el VSIAF y eliminar responsables. El resto
     * (p. ej. APOYO) registra y edita: sus cambios viajan solos al VSIAF por el worker.
     * El chequeo va en el servidor, no solo ocultando botones: en este proyecto casi
     * todas las rutas son permitAll() y cualquiera puede llamar al endpoint a mano.
     */
    private static boolean esAdmin(HttpServletRequest request) {
        return RolesSciaf.esAdministrativo(request);
    }

    private static ResponseEntity<Map<String, Object>> soloAdmin() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                "msg", "Solo un ADMINISTRADOR o SUPER USUARIO puede hacer esta operación."));
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioResponsable(Model model, HttpServletRequest request) {
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        model.addAttribute("esAdmin", esAdmin(request));
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
            @RequestParam(name = "oficinaId", required = false) Long oficinaId,
            HttpServletRequest request) {

        // Auditoría (quién registró / modificó): solo ADMINISTRADOR / SUPER USUARIO, y ni se envía a los demás.
        boolean admin = esAdmin(request);

        int size = (length < 0) ? 1000 : length;
        int page = Math.max(start, 0) / Math.max(size, 1);
        Pageable pageable = PageRequest.of(page, size);
        Page<IResposableDao.ResponsableRow> p = responsableService.datatable(search, oficinaId, pageable);

        List<Map<String, Object>> data = new ArrayList<>(p.getNumberOfElements());

        // Estado real del envío al VSIAF (cola del worker) y auditoría de la página visible.
        Map<Long, Boolean> pendientes = new HashMap<>();
        for (var row : p.getContent()) pendientes.put(row.getIdResponsable(), Boolean.TRUE.equals(row.getPendienteDbf()));
        Map<Long, VsiafApoyoService.EstadoVsiaf> estados = vsiafApoyoService.estados(VsiafApoyoService.TABLA_RESP, pendientes);
        Set<Long> conSolicitud = autorizacionService.idsConSolicitudPendiente(ActividadService.MOD_RESPONSABLE, pendientes.keySet());

        Map<Long, Responsable> entidades = new HashMap<>();
        if (admin) {
            for (Responsable r : responsableDao.findAllById(pendientes.keySet())) entidades.put(r.getIdResponsable(), r);
        }
        Set<Long> idsUsuarios = new HashSet<>();
        for (Responsable r : entidades.values()) {
            idsUsuarios.add(r.getRegistroIdUsuario());
            idsUsuarios.add(r.getModificacionIdUsuario());
        }
        Map<Long, String> nombresUsuario = vsiafApoyoService.nombresUsuario(idsUsuarios);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (var row : p.getContent()) {
            String idEnc = "";
            try { idEnc = Encriptar.encrypt(String.valueOf(row.getIdResponsable())); } catch (Exception e) {}

            // "No en DBF" = registro creado en SCIAF aún NO subido/confirmado al VSIAF (pendiente_dbf).
            // Ya NO se usa apiEstado (que espeja el DBF) ni se lee RESP.DBF por CIFS en cada página;
            // el cruce autoritativo BD↔DBF vive en Conciliación.
            boolean enDbf = !Boolean.TRUE.equals(row.getPendienteDbf());

            Map<String, Object> m = new HashMap<>();
            m.put("idEnc", idEnc);
            m.put("codFun", nvl(row.getCodFun()));
            m.put("nombre", nvl(row.getNombre()));
            m.put("paterno", nvl(row.getPaterno()));
            m.put("materno", nvl(row.getMaterno()));
            m.put("ci", nvl(row.getCi()));
            m.put("oficina", nvl(row.getOficina()));
            m.put("cargo", nvl(row.getCargo()));
            m.put("idResponsable", row.getIdResponsable());
            m.put("existeEnDbf", enDbf);

            VsiafApoyoService.EstadoVsiaf est = estados.get(row.getIdResponsable());
            m.put("estadoVsiaf", est != null ? est.codigo() : VsiafApoyoService.EST_VSIAF);
            m.put("estadoTexto", est != null ? est.texto() : "En VSIAF");
            m.put("estadoDetalle", est != null ? est.detalle() : "");
            m.put("solicitudPendiente", conSolicitud.contains(row.getIdResponsable()));

            Responsable ent = admin ? entidades.get(row.getIdResponsable()) : null;
            m.put("registradoPor", ent != null ? nvl(nombresUsuario.get(ent.getRegistroIdUsuario())) : "");
            m.put("fechaRegistro", ent != null && ent.getRegistro() != null ? fmt.format(aLocal(ent.getRegistro())) : "");
            m.put("modificadoPor", ent != null ? nvl(nombresUsuario.get(ent.getModificacionIdUsuario())) : "");
            m.put("fechaModificacion", ent != null && ent.getModificacion() != null ? fmt.format(aLocal(ent.getModificacion())) : "");
            m.put("usuarioVsiaf", ent != null ? nvl(ent.getUsuario()) : "");
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
    @RequestMapping("/formulario")
    public String formularioResponsable(Model model, HttpServletRequest request,
            @RequestParam(required = false) String id) { 
        Responsable responsable = new Responsable();
        
        if (id != null && !id.isEmpty()) {
            try {
                Long idDec = Long.parseLong(Encriptar.decrypt(id));
                Responsable encontrado = responsableService.findByIdWithRelations(idDec);
                if (encontrado != null) {
                    responsable = encontrado;
                }
            } catch (Exception e) {
                log.error("Error cargando responsable ID: " + id, e);
            }
        }

        if(responsable.getCodExp() == null) responsable.setCodExp(Short.valueOf("9"));

        // Con bienes a cargo, la oficina y el código (la clave en el VSIAF) no se tocan.
        long activosACargo = (responsable.getIdResponsable() != null)
                ? responsableDao.contarActivosAsignados(responsable.getIdResponsable()) : 0;
        model.addAttribute("activosACargo", activosACargo);
        model.addAttribute("bloquearClave", activosACargo > 0);
        boolean admin = esAdmin(request);
        model.addAttribute("esAdmin", admin);
        if (responsable.getIdResponsable() != null && admin) {
            Map<Long, String> nombres = vsiafApoyoService.nombresUsuario(
                    java.util.Arrays.asList(responsable.getRegistroIdUsuario(), responsable.getModificacionIdUsuario()));
            model.addAttribute("registradoPor", nombres.get(responsable.getRegistroIdUsuario()));
            model.addAttribute("modificadoPor", nombres.get(responsable.getModificacionIdUsuario()));
        }

        model.addAttribute("responsable", responsable);
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
            @RequestParam(required = false) String ci,
            @RequestParam(required = false, defaultValue = "9") Short codExp,
            @RequestParam(required = false) String codigoFuncionario,
            @RequestParam Long idOficina,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String paterno,
            @RequestParam(required = false) String materno,
            @RequestParam(required = false) String correo,
            @RequestParam(value = "cargoApi", required = false) String cargoApi,
            @RequestParam(value = "cargoNombre", required = false) String cargoNombreParam,
            @RequestParam(defaultValue = "false") boolean modoRapido,
            @RequestParam(defaultValue = "false") boolean forzarCreacion) {

        // Distintos formularios mandan el cargo con nombre distinto (cargoApi / cargoNombre): aceptar ambos.
        String cargoNombre = (cargoApi != null && !cargoApi.isBlank()) ? cargoApi : cargoNombreParam;

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

        try {
            Oficina oficina = oficinaService.findById(idOficina);
            if (oficina == null || !"ACTIVO".equals(oficina.getEstado())) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No se encontró la oficina especificada."));
            }

            // modoRapido (alta al vuelo desde Activos / Asignación / Transferencia): queda
            // pendiente y viaja al VSIAF junto con el movimiento que lo usa. El alta desde
            // este módulo va al VSIAF enseguida.
            ResponsableAltaService.ResultadoAlta alta = responsableAltaService.registrar(
                    new ResponsableAltaService.DatosAlta(ci, codExp, codigoFuncionario, codigoApi,
                            nombre, paterno, materno, correo, cargoNombre),
                    oficina, usuario, forzarCreacion, modoRapido);
            Responsable responsable = alta.responsable();
            Responsable cargadoAlta = responsableService.findByIdWithRelations(responsable.getIdResponsable());
            actividadService.registrar(usuario, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_REGISTRO,
                    ResponsableGestionService.referencia(cargadoAlta),
                    "Registró al responsable " + ResponsableGestionService.referencia(cargadoAlta)
                    + (modoRapido ? " (alta rápida desde otro módulo)" : ""), responsable.getIdResponsable());

            if (modoRapido) {
                return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "msg", "Responsable registrado. Se enviará al VSIAF junto con el movimiento que lo utilice.",
                    "id", responsable.getIdResponsable(),
                    "personaNueva", alta.personaNueva()
                ));
            }

            VsiafApoyoService.Envio envio = vsiafApoyoService.insertarResponsable(cargadoAlta, usuarioNombre);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "vsiafOk", envio.ok(),
                "msg", "Responsable registrado" + (alta.personaNueva() ? " (nueva persona)" : "") + ". " + envio.mensaje(),
                "id", responsable.getIdResponsable(),
                "personaNueva", alta.personaNueva()
            ));

        } catch (ResponsableAltaService.PersonasSimilaresException similares) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(similares.cuerpo());
        } catch (IllegalArgumentException invalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", invalido.getMessage()));
        } catch (Exception e) {
            log.error("Error registrando responsable: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error al registrar: " + e.getMessage()));
        }
    }

    /**
     * Edición. Datos de la persona y cargo se aplican siempre. Cambiar oficina o código
     * (la clave en el VSIAF) lo aplica directo un ADMINISTRADOR / SUPER USUARIO; los demás
     * generan una solicitud de autorización (ver {@code OficinaController.modificar_oficina}).
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-responsable")
    @ResponseBody
    public ResponseEntity<?> modificarResponsable(
            HttpServletRequest request,
            @RequestParam String idResponsableEnc,
            @RequestParam(required = false) String ci,
            @RequestParam(required = false, defaultValue = "9") Short codExp,
            @RequestParam(required = false) String codigoFuncionario,
            @RequestParam Long idOficina,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String paterno,
            @RequestParam(required = false) String materno,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) String cargoApi,
            @RequestParam(required = false) String codigoApi,
            @RequestParam(defaultValue = "false") boolean solicitarAutorizacion,
            @RequestParam(required = false) String motivoSolicitud) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");

        try {
            Long id = Long.parseLong(Encriptar.decrypt(idResponsableEnc));
            DatosResponsable datos = new DatosResponsable(id, ci, codExp, codigoFuncionario, idOficina,
                    nombre, paterno, materno, correo, cargoApi, codigoApi);
            Responsable original = responsableGestionService.buscar(id);

            if (responsableGestionService.cambiaClave(original, datos) && !esAdmin(request)) {
                responsableGestionService.validarModificacion(datos);   // que no se pida algo imposible
                String resumen = responsableGestionService.describirCambios(original, datos);
                if (!solicitarAutorizacion) {
                    return ResponseEntity.ok(Map.of("ok", false, "requiereAutorizacion", true,
                        "msg", "Cambiar la oficina o el código de un responsable requiere autorización. Cambios: " + resumen));
                }
                autorizacionService.solicitar(ResponsableGestionService.TIPO_MODIFICAR, ActividadService.MOD_RESPONSABLE,
                        id, ResponsableGestionService.referencia(original), resumen, datos.aMapa(),
                        motivoSolicitud, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                    "msg", "Solicitud enviada. Se aplicará cuando el revisor la apruebe; le avisaremos aquí mismo."));
            }

            ResponsableGestionService.Resultado r = responsableGestionService.modificar(datos, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "vsiafOk", r.vsiafOk(), "msg", r.msg()));

        } catch (IllegalArgumentException | IllegalStateException invalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", invalido.getMessage()));
        } catch (Exception e) {
            log.error("Error fatal modificando: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    /**
     * Alta después de que el usuario confirmó que, pese a haber personas con un nombre
     * parecido, se trata de alguien nuevo. Antes este endpoint le pasaba
     * {@code forzarCreacion} a {@code registrarResponsable} en el lugar de
     * {@code modoRapido}: la búsqueda de similares se repetía, devolvía otra vez 409 y la
     * confirmación no servía de nada.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-responsable-forzado")
    @ResponseBody
    public ResponseEntity<?> registrarResponsableForzado(
            HttpServletRequest request,
            @RequestParam(required = false) String codigoApi,
            @RequestParam(required = false) String ci,
            @RequestParam(required = false, defaultValue = "9") Short codExp,
            @RequestParam(required = false) String codigoFuncionario,
            @RequestParam Long idOficina,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String paterno,
            @RequestParam(required = false) String materno,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) String cargoApi,
            @RequestParam(required = false) String nombreCargoApi,
            @RequestParam(value = "cargoNombre", required = false) String cargoNombre,
            @RequestParam(defaultValue = "false") boolean modoRapido) {

        String cargoEfectivo = (nombreCargoApi != null && !nombreCargoApi.isBlank()) ? nombreCargoApi
                : (cargoApi != null && !cargoApi.isBlank()) ? cargoApi : cargoNombre;
        return registrarResponsable(request, codigoApi, ci, codExp, codigoFuncionario, idOficina,
                nombre, paterno, materno, correo, cargoEfectivo, null, modoRapido, true);
    }

    private boolean esCiValido(String ci) {
        if (ci == null || ci.isBlank()) {
            return false;
        }
        
        String ciLimpio = ci.trim().replaceAll("[.\\-\\s]", "");
        return ciLimpio.matches("\\d{5,}");
    }

    /** Sincronización manual desde el DBF: solo administradores (el resto no la necesita). */
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> syncFromMountedManual(
            HttpServletRequest request,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {

        if (!esAdmin(request)) return soloAdmin();
        return syncFromMounted(q, forzarCompleto);
    }

    /** La usa también {@code SyncOrchestrator} (sincronización programada), sin petición HTTP. */
    @Transactional
    public ResponseEntity<?> syncFromMounted(String q, boolean forzarCompleto) {

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
                // Viene leído del DBF → por definición está en el VSIAF: no pendiente.
                responsable.setPendienteDbf(false);

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

    @GetMapping("/api/cargos/search")
    @ResponseBody
    public List<Map<String, String>> buscarCargos(@RequestParam(required = false) String q) {

        List<Cargo> cargos = (q == null || q.isBlank()) 
            ? cargoService.findAll() 
            : cargoService.buscarPorNombreLike("%" + q.toUpperCase() + "%");
            
        return cargos.stream()
            .limit(20)
            .map(c -> Map.of("nombre", c.getNombre()))
            .collect(Collectors.toList());
    }
    
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

    /**
     * Reenvía al VSIAF un responsable que quedó pendiente o que el worker rechazó. Es el
     * único "sincronizar" que ve un usuario que no es administrador: actúa sobre un solo
     * registro y usa la misma cola que el alta.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/subir-dbf/{id}")
    @ResponseBody
    public ResponseEntity<?> subirResponsableADbf(@PathVariable("id") String idEnc, HttpServletRequest request) {
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            Responsable resp = responsableService.findByIdWithRelations(id);
            if (resp == null) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No encontrado"));

            Usuario u = (Usuario) request.getSession().getAttribute("usuario");
            VsiafApoyoService.Envio envio = vsiafApoyoService.reenviarResponsable(resp, u != null ? u.getUsuario() : "SISTEMA");
            actividadService.registrar(u, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_REENVIO,
                    ResponsableGestionService.referencia(resp), "Reenvió al VSIAF al responsable "
                    + ResponsableGestionService.referencia(resp) + (envio.ok() ? "" : " (falló: " + envio.mensaje() + ")"),
                    resp.getIdResponsable());
            return ResponseEntity.ok(Map.of("ok", envio.ok(), "msg", envio.mensaje()));

        } catch (Exception e) {
            log.error("Error subiendo responsable", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    /**
     * Baja lógica en el SCIAF, solo sin bienes a cargo (en el VSIAF la fila sigue). Un
     * ADMINISTRADOR / SUPER USUARIO la aplica directo; el resto envía una solicitud con
     * {@code motivo}. Antes la vista llamaba a este endpoint pero no existía.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id}")
    @ResponseBody
    public ResponseEntity<?> eliminarResponsable(@PathVariable("id") String idEnc, HttpServletRequest request,
            @RequestParam(required = false) String motivo) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            Responsable resp = responsableGestionService.buscar(id);
            String dep = responsableGestionService.dependenciasQueImpidenEliminar(resp);
            if (dep != null) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", dep));

            if (!esAdmin(request)) {
                autorizacionService.solicitar(ResponsableGestionService.TIPO_ELIMINAR, ActividadService.MOD_RESPONSABLE,
                        id, ResponsableGestionService.referencia(resp), "Eliminar al responsable",
                        Map.of("idResponsable", id), motivo, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                        "msg", "Solicitud de eliminación enviada. Se aplicará cuando el revisor la apruebe."));
            }
            ResponsableGestionService.Resultado r = responsableGestionService.eliminar(id, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "msg", r.msg()));

        } catch (IllegalArgumentException | IllegalStateException invalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", invalido.getMessage()));
        } catch (Exception e) {
            log.error("Error eliminando responsable", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String generarClaveUnica(String entidad, String unidad, Short codOfi, String codResp) {

        String e = (entidad == null) ? "" : entidad.trim().toUpperCase().replaceAll("\\p{C}", "");
        String u = (unidad == null) ? "" : unidad.trim().toUpperCase().replaceAll("\\p{C}", "");
        String cOfi = (codOfi == null) ? "0" : String.valueOf(codOfi);
        String cResp = "0";
        if (codResp != null) {
            String soloNumeros = codResp.replaceAll("\\D+", ""); 
            if (!soloNumeros.isEmpty()) {
                try {
                    cResp = String.valueOf(Integer.parseInt(soloNumeros));
                } catch (NumberFormatException ex) {
                    cResp = soloNumeros;
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

    private static LocalDateTime aLocal(Date d) {
        return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
    }
}