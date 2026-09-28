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
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.ActividadService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.AutorizacionService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.OficinaGestionService;
import com.usic.SistemasActivosFijosUAP.model.service.supervision.OficinaGestionService.DatosOficina;
import com.usic.SistemasActivosFijosUAP.config.RolesSciaf;

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
    private final SyncControlService syncControlService;
    private final VsiafApoyoService vsiafApoyoService;
    private final ResponsableAltaService responsableAltaService;
    private final IResponsableService responsableService;
    private final IOficinaDao oficinaDao;
    private final OficinaGestionService oficinaGestionService;
    private final AutorizacionService autorizacionService;
    private final ActividadService actividadService;

    private static final Logger log = LoggerFactory.getLogger(OficinaController.class);

    /**
     * ADMINISTRADOR / SUPER USUARIO: sincronizan a mano, ven la auditoría y aplican directo
     * los cambios de alto impacto. El resto (p. ej. APOYO) registra y edita; para eliminar
     * o cambiar la clave (predio/código) genera una solicitud de autorización.
     */
    private static boolean esAdmin(HttpServletRequest request) {
        return RolesSciaf.esAdministrativo(request);
    }

    private static final String MSG_SOLO_ADMIN = "Solo un ADMINISTRADOR o SUPER USUARIO puede hacer esta operación.";

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_oficina(Model model) {
        loadSyncInfo(model);
        return "oficina/vista";
    }

    /**
     * Datos de la última sincronización con el VSIAF. Los usa la cabecera de la tabla,
     * que es un fragmento aparte: antes solo se cargaban en /vista y el fragmento
     * mostraba siempre "Nunca".
     */
    private void loadSyncInfo(Model model) {
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
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_oficina(Model model, HttpServletRequest request,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida) {

        try {
            // Cargar SOLO de PostgreSQL (rápido). Ya NO se lee el DBF por CIFS en cada render:
            // el cruce autoritativo BD↔DBF vive en el módulo Conciliación.
            List<Oficina> listasOficinas = oficinaService.buscarPorQ(q);

            List<String> encryptedIds = new ArrayList<>();
            if (listasOficinas != null) {
                for (Oficina o : listasOficinas) {
                    // "No en DBF" = registro creado en SCIAF aún NO subido/confirmado al VSIAF (pendiente_dbf).
                    // Ya no se usa apiEstado (que espeja el DBF); el cruce real vive en Conciliación.
                    o.setExisteEnDbf(!o.isPendienteDbf());
                    encryptedIds.add(o.getIdOficina() == null ? "" : Encriptar.encrypt(Long.toString(o.getIdOficina())));
                }
            }

            model.addAttribute("listasOficinas", listasOficinas);
            model.addAttribute("id_encryptado", encryptedIds);
            model.addAttribute("sourceUsed", "db");

            // Estado real del envío al VSIAF (cola del worker) y quién registró / modificó.
            Map<Long, Boolean> pendientes = new java.util.HashMap<>();
            Set<Long> idsUsuarios = new HashSet<>();
            if (listasOficinas != null) {
                for (Oficina o : listasOficinas) {
                    if (o.getIdOficina() == null) continue;
                    pendientes.put(o.getIdOficina(), o.isPendienteDbf());
                    idsUsuarios.add(o.getRegistroIdUsuario());
                    idsUsuarios.add(o.getModificacionIdUsuario());
                }
            }
            model.addAttribute("estadosVsiaf", vsiafApoyoService.estados(VsiafApoyoService.TABLA_OFICINA, pendientes));
            model.addAttribute("conSolicitud", autorizacionService.idsConSolicitudPendiente(
                    ActividadService.MOD_OFICINA, pendientes.keySet()));
            // Auditoría: solo la ven ADMINISTRADOR / SUPER USUARIO (ni se manda a los demás).
            model.addAttribute("nombresUsuario", esAdmin(request) ? vsiafApoyoService.nombresUsuario(idsUsuarios) : Map.of());

        } catch (Exception e) {
            log.error("Error cargando tabla oficinas", e);
            model.addAttribute("error", "Error cargando datos: " + e.getMessage());
        }
        model.addAttribute("esAdmin", esAdmin(request));
        loadSyncInfo(model);
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
    @RequestMapping("/formulario")
    public String formulario_oficina(Model model,
            @RequestParam(name = "embebido", defaultValue = "false") boolean embebido) {
        model.addAttribute("oficina", new Oficina());
        model.addAttribute("predios", predioServicio.findAll());
        // Embebido = abierto desde el alta de responsable: ahí no tiene sentido ofrecer
        // registrar otro responsable junto con la oficina.
        model.addAttribute("embebido", embebido);
        return "oficina/formulario";
    }

    @GetMapping("/siguiente-codigo/{idPredio}")
    public ResponseEntity<Short> getSiguienteCodigoOficina(@PathVariable Long idPredio) {
        Short siguienteCodOfi = oficinaService.findNextCodOfiByPredioId(idPredio);
        return ResponseEntity.ok(siguienteCodOfi);
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_oficina}")
    public String formularioEdit_oficina(Model model, HttpServletRequest request,
            @PathVariable("id_oficina") String idOficina) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idOficina));
        Oficina oficina = oficinaService.findById(id);
        model.addAttribute("oficina", oficina);
        boolean admin = esAdmin(request);
        model.addAttribute("esAdmin", admin);
        if (oficina != null && admin) {
            Map<Long, String> nombres = vsiafApoyoService.nombresUsuario(
                    java.util.Arrays.asList(oficina.getRegistroIdUsuario(), oficina.getModificacionIdUsuario()));
            model.addAttribute("registradoPor", nombres.get(oficina.getRegistroIdUsuario()));
            model.addAttribute("modificadoPor", nombres.get(oficina.getModificacionIdUsuario()));
        }
        model.addAttribute("predios", predioServicio.findAll());
        model.addAttribute("edit", "true");
        // Con responsables o bienes, predio y código (la clave en el VSIAF) no se tocan.
        long responsables = oficinaDao.contarResponsablesVigentes(id);
        long activos = oficinaDao.contarActivos(id);
        model.addAttribute("responsablesOficina", responsables);
        model.addAttribute("activosOficina", activos);
        model.addAttribute("bloquearClave", responsables + activos > 0);
        return "oficina/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-oficina")
    @ResponseBody
    public ResponseEntity<?> registrar_oficina(
            HttpServletRequest request,
            @Validated @ModelAttribute Oficina oficina,
            BindingResult br,
            @RequestParam(defaultValue = "false") boolean modoRapido,
            // Alta del responsable junto con la oficina (opcional, solo desde este módulo)
            @RequestParam(defaultValue = "false") boolean conResponsable,
            @RequestParam(required = false) String respCi,
            @RequestParam(required = false, defaultValue = "9") Short respCodExp,
            @RequestParam(required = false) String respNombre,
            @RequestParam(required = false) String respPaterno,
            @RequestParam(required = false) String respMaterno,
            @RequestParam(required = false) String respCorreo,
            @RequestParam(required = false) String respCargo,
            @RequestParam(required = false) String respCodigoFuncionario,
            @RequestParam(defaultValue = "false") boolean respForzar) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");

        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
            ));
        }

        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

        // Un alta nunca pisa una oficina existente, aunque el cliente mande un id.
        oficina.setIdOficina(null);
        Predio predio = (oficina.getPredio() != null && oficina.getPredio().getIdPredio() != null)
                ? predioServicio.findById(oficina.getPredio().getIdPredio()) : null;
        String invalido = oficinaGestionService.validar(predio, oficina.getCodOfi(), oficina.getNombre(), null);
        if (invalido != null) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", invalido));
        }

        oficina.setPredio(predio);
        oficina.setNombre(oficina.getNombre().trim());
        oficina.setObserv(oficina.getObserv() != null && !oficina.getObserv().isBlank() ? oficina.getObserv().trim() : null);
        oficina.setEstado("ACTIVO");
        oficina.setFechaUlt(LocalDate.now());
        oficina.setUsuario(usuarioNombre);
        oficina.setApiEstado(modoRapido ? Short.valueOf("3") : Short.valueOf("1"));
        // modoRapido (alta al vuelo desde Registro de Activos) queda pendiente y viaja al
        // VSIAF cuando se aprueba el activo que la usa. El alta desde este módulo va enseguida.
        oficina.setPendienteDbf(modoRapido);
        if (usuario != null) oficina.setRegistroIdUsuario(usuario.getIdUsuario());

        if (!conResponsable || modoRapido) {
            oficinaService.save(oficina);
            registrarActividadAlta(usuario, oficina, modoRapido ? " (desde Registro de Activos)" : "");
            if (modoRapido) {
                return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "msg", "Oficina registrada. Se enviará al VSIAF junto con el activo cuando se apruebe.",
                    "id", oficina.getIdOficina()
                ));
            }
            VsiafApoyoService.Envio envio = vsiafApoyoService.insertarOficina(oficina, usuarioNombre);
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "vsiafOk", envio.ok(),
                "msg", "Oficina registrada. " + envio.mensaje(),
                "id", oficina.getIdOficina()
            ));
        }

        // ── Oficina + su responsable, todo o nada ─────────────────────────────
        ResponsableAltaService.ResultadoAlta alta;
        try {
            alta = responsableAltaService.registrarOficinaConResponsable(oficina,
                    new ResponsableAltaService.DatosAlta(respCi, respCodExp, respCodigoFuncionario, null,
                            respNombre, respPaterno, respMaterno, respCorreo, respCargo),
                    usuario, respForzar);
        } catch (ResponsableAltaService.PersonasSimilaresException similares) {
            return ResponseEntity.status(409).body(similares.cuerpo());
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Responsable: " + datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("Error registrando oficina con responsable", e);
            return ResponseEntity.status(500).body(Map.of("ok", false,
                    "msg", "No se registró nada (ni oficina ni responsable): " + e.getMessage()));
        }

        // Primero la oficina: el worker procesa por nombre de archivo (OFICINA_… antes que RESP_…).
        VsiafApoyoService.Envio envOfi = vsiafApoyoService.insertarOficina(oficina, usuarioNombre);
        com.usic.SistemasActivosFijosUAP.model.entity.Responsable respCargado =
                responsableService.findByIdWithRelations(alta.responsable().getIdResponsable());
        VsiafApoyoService.Envio envResp = vsiafApoyoService.insertarResponsable(respCargado, usuarioNombre);

        registrarActividadAlta(usuario, oficina, " junto con su responsable");
        actividadService.registrar(usuario, ActividadService.MOD_RESPONSABLE, ActividadService.ACC_REGISTRO,
                com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService.referencia(respCargado),
                "Registró al responsable "
                + com.usic.SistemasActivosFijosUAP.model.service.supervision.ResponsableGestionService.referencia(respCargado)
                + " en la nueva oficina", respCargado.getIdResponsable());

        String msgVsiaf = (envOfi.ok() && envResp.ok()) ? envOfi.mensaje()
                : (!envOfi.ok() ? "Oficina: " + envOfi.mensaje() + " " : "")
                  + (!envResp.ok() ? "Responsable: " + envResp.mensaje() : "");
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "vsiafOk", envOfi.ok() && envResp.ok(),
            "msg", "Oficina y responsable registrados" + (alta.personaNueva() ? " (nueva persona)" : "") + ". " + msgVsiaf,
            "id", oficina.getIdOficina(),
            "idResponsable", alta.responsable().getIdResponsable()
        ));
    }

    /**
     * Edición. Nombre y observaciones se aplican siempre. Cambiar predio o código (la clave
     * en el VSIAF) lo aplica directo un ADMINISTRADOR / SUPER USUARIO; los demás generan
     * una solicitud de autorización:
     * <ol>
     *   <li>Sin {@code solicitarAutorizacion}: se valida y se responde
     *       {@code requiereAutorizacion=true} para que la pantalla pida el motivo.</li>
     *   <li>Con {@code solicitarAutorizacion=true} + {@code motivoSolicitud}: se crea la
     *       solicitud y no se aplica nada hasta que el revisor la apruebe.</li>
     * </ol>
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-oficina")
    @ResponseBody
    public ResponseEntity<?> modificar_oficina(
            HttpServletRequest request,
            @Validated @ModelAttribute Oficina oficinaForm,
            BindingResult br,
            @RequestParam(defaultValue = "false") boolean solicitarAutorizacion,
            @RequestParam(required = false) String motivoSolicitud) {

        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "errors", br.getFieldErrors().stream()
                        .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                        .toList()
            ));
        }

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        DatosOficina datos = new DatosOficina(oficinaForm.getIdOficina(),
                oficinaForm.getPredio() != null ? oficinaForm.getPredio().getIdPredio() : null,
                oficinaForm.getCodOfi(), oficinaForm.getNombre(), oficinaForm.getObserv());

        try {
            Oficina original = oficinaGestionService.buscar(datos.idOficina());
            boolean cambiaClave = oficinaGestionService.cambiaClave(original, datos);

            if (cambiaClave && !esAdmin(request)) {
                oficinaGestionService.validarModificacion(datos);   // que no se pida algo imposible
                String resumen = oficinaGestionService.describirCambios(original, datos);
                if (!solicitarAutorizacion) {
                    return ResponseEntity.ok(Map.of("ok", false, "requiereAutorizacion", true,
                        "msg", "Cambiar el predio o el código de una oficina requiere autorización. Cambios: " + resumen));
                }
                autorizacionService.solicitar(OficinaGestionService.TIPO_MODIFICAR, ActividadService.MOD_OFICINA,
                        original.getIdOficina(), OficinaGestionService.referencia(original), resumen,
                        datos.aMapa(), motivoSolicitud, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                    "msg", "Solicitud enviada. Se aplicará cuando el revisor la apruebe; le avisaremos aquí mismo."));
            }

            OficinaGestionService.Resultado r = oficinaGestionService.modificar(datos, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "vsiafOk", r.vsiafOk(), "msg", r.msg()));

        } catch (IllegalArgumentException | IllegalStateException invalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", invalido.getMessage()));
        }
    }

    private void registrarActividadAlta(Usuario usuario, Oficina oficina, String detalle) {
        actividadService.registrar(usuario, ActividadService.MOD_OFICINA, ActividadService.ACC_REGISTRO,
                OficinaGestionService.referencia(oficina),
                "Registró la oficina " + OficinaGestionService.referencia(oficina) + detalle, oficina.getIdOficina());
    }

    /**
     * Baja lógica en el SCIAF (en el VSIAF la fila sigue existiendo), solo sin
     * responsables ni bienes. Un ADMINISTRADOR / SUPER USUARIO la aplica directo; el resto
     * envía una solicitud de autorización con {@code motivo}.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_oficina}")
    @ResponseBody
    public ResponseEntity<?> eliminar(HttpServletRequest request,
            @PathVariable("id_oficina") String idOficina,
            @RequestParam(required = false) String motivo) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idOficina));
            Oficina oficina = oficinaGestionService.buscar(id);
            String dep = oficinaGestionService.dependenciasQueImpidenEliminar(oficina);
            if (dep != null) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", dep));

            if (!esAdmin(request)) {
                autorizacionService.solicitar(OficinaGestionService.TIPO_ELIMINAR, ActividadService.MOD_OFICINA,
                        id, OficinaGestionService.referencia(oficina), "Eliminar la oficina",
                        Map.of("idOficina", id), motivo, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                        "msg", "Solicitud de eliminación enviada. Se aplicará cuando el revisor la apruebe."));
            }
            OficinaGestionService.Resultado r = oficinaGestionService.eliminar(id, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "msg", r.msg()));

        } catch (IllegalArgumentException | IllegalStateException invalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", invalido.getMessage()));
        } catch (Exception e) {
            log.error("Error eliminando oficina", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    /**
     * Reenvía al VSIAF una oficina que quedó pendiente o que el worker rechazó. Actúa
     * sobre un solo registro y usa la misma cola que el alta.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/subir-dbf/{id_oficina}")
    @ResponseBody
    public ResponseEntity<?> subirOficinaADbf(HttpServletRequest request,
                                              @PathVariable("id_oficina") String idOficinaEnc) {
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idOficinaEnc));
            Oficina oficina = oficinaService.findById(id);
            if (oficina == null) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Oficina no encontrada"));

            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            VsiafApoyoService.Envio envio = vsiafApoyoService.reenviarOficina(oficina,
                    usuario != null ? usuario.getUsuario() : "SISTEMA");
            actividadService.registrar(usuario, ActividadService.MOD_OFICINA, ActividadService.ACC_REENVIO,
                    OficinaGestionService.referencia(oficina), "Reenvió al VSIAF la oficina "
                    + OficinaGestionService.referencia(oficina) + (envio.ok() ? "" : " (falló: " + envio.mensaje() + ")"),
                    oficina.getIdOficina());
            return ResponseEntity.ok(Map.of("ok", envio.ok(), "msg", envio.mensaje()));

        } catch (Exception e) {
            log.error("Error subiendo a DBF", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    /** Sincronización manual desde el DBF: solo administradores (el resto no la necesita). */
    @ValidarUsuarioAutenticado
    @PostMapping("/sync-from-mounted")
    @ResponseBody
    public ResponseEntity<?> syncFromMountedManual(
            HttpServletRequest request,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "gestion", required = false) Short gestionPreferida,
            @RequestParam(name = "forzarCompleto", defaultValue = "false") boolean forzarCompleto) {

        if (!esAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("ok", false, "message", MSG_SOLO_ADMIN));
        }
        return syncFromMounted(q, gestionPreferida, forzarCompleto);
    }

    /** La usa también {@code SyncOrchestrator} (sincronización programada), sin petición HTTP. */
    public ResponseEntity<?> syncFromMounted(String q, Short gestionPreferida, boolean forzarCompleto) {

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
                // Viene leído del DBF → por definición está en el VSIAF: no pendiente.
                oficina.setPendienteDbf(false);

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