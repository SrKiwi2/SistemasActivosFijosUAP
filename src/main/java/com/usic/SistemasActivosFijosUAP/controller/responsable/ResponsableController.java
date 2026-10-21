package com.usic.SistemasActivosFijosUAP.controller.responsable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import com.usic.SistemasActivosFijosUAP.interoperabilidad.TextoVsiaf;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IOficinaDao;
import com.usic.SistemasActivosFijosUAP.model.dao.IResposableDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SyncResult;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesResponsableRepo;
import com.usic.SistemasActivosFijosUAP.model.service.ResponsableAltaService;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;
import com.usic.SistemasActivosFijosUAP.model.service.VsiafApoyoService;
import com.usic.SistemasActivosFijosUAP.model.service.personas.IdentidadPersona;
import com.usic.SistemasActivosFijosUAP.model.service.personas.ResolutorPersonaSync;
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
    private final com.usic.SistemasActivosFijosUAP.model.service.AvisoPendienteVsiafService avisoPendienteVsiaf;
    private final IResposableDao responsableDao;
    private final ResponsableGestionService responsableGestionService;
    private final AutorizacionService autorizacionService;
    private final ActividadService actividadService;
    private final IOficinaDao oficinaDao;
    /** Sin VSIAF a la vista (laptop de desarrollo, montaje caído) la sincronización avisa en vez de leer 0 registros. */
    private final com.usic.SistemasActivosFijosUAP.componet.VsiafDisponibilidad vsiaf;
    private final IDbfColaOrdenDao colaOrdenDao;

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

    /**
     * La pantalla llega sin datos pesados: la tabla se pagina en el servidor y el filtro de
     * oficina busca a medida que se escribe (antes la vista y el formulario traían TODAS las
     * oficinas y consultaban el predio de cada una por separado).
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicioResponsable(Model model, HttpServletRequest request) {
        boolean admin = esAdmin(request);
        model.addAttribute("esAdmin", admin);
        if (admin) {
            try {
                SyncControl s = syncControlService.obtenerInfoSincronizacion("responsable");
                if (s != null) {
                    model.addAttribute("ultimaSincronizacion",
                            s.getUltimaSincronizacion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
                    model.addAttribute("estadoSync", s.getEstado());
                    model.addAttribute("registrosProcesados", s.getRegistrosProcesados());
                    model.addAttribute("registrosNuevos", s.getRegistrosNuevos());
                    model.addAttribute("registrosActualizados", s.getRegistrosActualizados());
                    model.addAttribute("duracionUltimaSync", s.getDuracionMs() / 1000.0);
                }
            } catch (Exception e) {
                model.addAttribute("estadoSync", "ERROR");
            }
        }
        return "responsable/vista";
    }

    /**
     * Buscador de oficinas (select2 del filtro y del formulario): hasta 40 por pedido, en una
     * consulta. Con {@code id} devuelve esa sola (para mostrar la recién creada).
     */
    @ValidarUsuarioAutenticado
    @GetMapping(value = "/api/oficinas/opciones", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public List<Map<String, Object>> oficinasOpciones(@RequestParam(required = false) String q,
            @RequestParam(required = false) Long id) {
        List<IOficinaDao.OficinaOpcion> lista;
        if (id != null) {
            lista = oficinaDao.opcion(id).map(List::of).orElse(List.of());
        } else {
            String patron = "%" + String.join("%", (q == null ? "" : q.trim().toUpperCase()).split("\\s+")) + "%";
            lista = oficinaDao.opciones(patron.replaceAll("%+", "%"), PageRequest.of(0, 40));
        }
        List<Map<String, Object>> out = new ArrayList<>(lista.size());
        for (IOficinaDao.OficinaOpcion o : lista) {
            out.add(Map.of("id", o.getId(), "text", textoOficina(o.getUnidad(), o.getCodOfi(), o.getNombre())));
        }
        return out;
    }

    /** "CAUN — 5 | SISTEMAS": como la muestran el filtro, el formulario y la tabla. */
    private static String textoOficina(String unidad, Short codOfi, String nombre) {
        return nvl(unidad) + " — " + (codOfi != null ? codOfi : "?") + " | " + nvl(nombre);
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
            @RequestParam(name = "estado", required = false, defaultValue = "ACTIVO") String estado,
            HttpServletRequest request) {

        // Auditoría (quién registró / modificó): solo ADMINISTRADOR / SUPER USUARIO, y ni se envía a los demás.
        boolean admin = esAdmin(request);

        int size = (length < 0) ? 1000 : length;
        int page = Math.max(start, 0) / Math.max(size, 1);
        Pageable pageable = PageRequest.of(page, size);
        Page<IResposableDao.ResponsableRow> p = responsableService.datatable(search, oficinaId,
                "INACTIVO".equals(estado) || "TODOS".equals(estado) ? estado : "ACTIVO", pageable);

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
            m.put("habilitado", !"INACTIVO".equals(row.getEstado()));

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
        // Al cambiar el cargo, el formulario ofrece llevarlo a las otras oficinas de la persona
        // (módulo Personas y cargos), si las tiene y si el usuario puede entrar ahí.
        if (responsable.getIdResponsable() != null && responsable.getPersona() != null) {
            final Long idEste = responsable.getIdResponsable();
            Long idPersona = responsable.getPersona().getIdPersona();
            long otras = responsableDao.findByPersonaIdPersona(idPersona).stream()
                    .filter(o -> "ACTIVO".equals(o.getEstado()) && !o.getIdResponsable().equals(idEste)).count();
            model.addAttribute("otrasOficinas", otras);
            model.addAttribute("idPersonaResp", idPersona);
            model.addAttribute("puedePersonasCargos",
                    com.usic.SistemasActivosFijosUAP.controller.personas.PersonasCargosController.puedeUsar(request));
        }
        if (responsable.getIdResponsable() != null && admin) {
            Map<Long, String> nombres = vsiafApoyoService.nombresUsuario(
                    java.util.Arrays.asList(responsable.getRegistroIdUsuario(), responsable.getModificacionIdUsuario()));
            model.addAttribute("registradoPor", nombres.get(responsable.getRegistroIdUsuario()));
            model.addAttribute("modificadoPor", nombres.get(responsable.getModificacionIdUsuario()));
        }

        model.addAttribute("responsable", responsable);
        model.addAttribute("idEnc", responsable.getIdResponsable() != null ? id : null);
        Oficina of = responsable.getOficina();
        if (of != null) {
            model.addAttribute("oficinaTexto", textoOficina(of.getPredio() != null ? of.getPredio().getUnidad() : null,
                    of.getCodOfi(), of.getNombre()));
        }
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
            @RequestParam(defaultValue = "false") boolean forzarCreacion,
            // false = no subirlo ahora: queda pendiente (pendienteDbf) y se avisa a los
            // administradores (lo usa Donaciones). Por omisión se sube como siempre.
            @RequestParam(defaultValue = "true") boolean subirVsiaf,
            @RequestParam(required = false) String desde) {

        // Distintos formularios mandan el cargo con nombre distinto (cargoApi / cargoNombre): aceptar ambos.
        String cargoNombre = (cargoApi != null && !cargoApi.isBlank()) ? cargoApi : cargoNombreParam;

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

        try {
            Oficina oficina = oficinaService.findById(idOficina);
            if (oficina == null || !"ACTIVO".equals(oficina.getEstado())) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No se encontró la oficina especificada."));
            }

            // modoRapido (alta al vuelo desde Activos / Asignación / Transferencia) también
            // va al VSIAF enseguida: así ya está allá cuando se encola el movimiento que lo
            // usa. Antes esperaba a ese movimiento y, si no llegaba, quedaba pendiente.
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

            if (!subirVsiaf) {
                if (!cargadoAlta.isPendienteDbf()) {
                    cargadoAlta.setPendienteDbf(true);
                    responsableService.save(cargadoAlta);
                }
                String origen = desde != null && !desde.isBlank() ? desde.trim() : "otro módulo";
                avisoPendienteVsiaf.responsablePendiente(responsable.getIdResponsable(),
                        ResponsableGestionService.referencia(cargadoAlta), usuarioNombre, origen);
                return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "vsiafOk", true,
                    "pendiente", true,
                    "msg", "Responsable registrado en el SCIAF" + (alta.personaNueva() ? " (nueva persona)" : "")
                         + ". Quedó PENDIENTE de subir al VSIAF; se avisó a los administradores.",
                    "id", responsable.getIdResponsable(),
                    "personaNueva", alta.personaNueva()
                ));
            }

            // Si su oficina también se creó al vuelo y sigue pendiente, va primero ella.
            List<String> avisosOficina = vsiafApoyoService.asegurarEnVsiaf(cargadoAlta.getOficina(), null, usuarioNombre);
            VsiafApoyoService.Envio envio = vsiafApoyoService.insertarResponsable(cargadoAlta, usuarioNombre);
            if (!avisosOficina.isEmpty()) {
                envio = new VsiafApoyoService.Envio(false, String.join(" ", avisosOficina) + " " + envio.mensaje());
            }

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
                // Foto de la persona al pedir: al aprobar, si entretanto la corrigieron, no se pisa.
                Map<String, Object> pedido = datos.aMapa();
                pedido.put(ResponsableGestionService.PERSONA_ANTES, ResponsableGestionService.nombreYCi(original.getPersona()));
                autorizacionService.solicitar(ResponsableGestionService.TIPO_MODIFICAR, ActividadService.MOD_RESPONSABLE,
                        id, ResponsableGestionService.referencia(original), resumen, pedido,
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
            @RequestParam(defaultValue = "false") boolean modoRapido,
            @RequestParam(defaultValue = "true") boolean subirVsiaf,
            @RequestParam(required = false) String desde) {

        String cargoEfectivo = (nombreCargoApi != null && !nombreCargoApi.isBlank()) ? nombreCargoApi
                : (cargoApi != null && !cargoApi.isBlank()) ? cargoApi : cargoNombre;
        return registrarResponsable(request, codigoApi, ci, codExp, codigoFuncionario, idOficina,
                nombre, paterno, materno, correo, cargoEfectivo, null, modoRapido, true, subirVsiaf, desde);
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
        if (!vsiaf.dbf("sincronización manual")) {
            return ResponseEntity.ok(Map.of("ok", false, "message", vsiaf.motivoDbf()));
        }
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
            List<Cargo> todosLosCargos = cargoService.findAll();
            Map<String, Cargo> cargosCache = cargarCargosEnCache(todosLosCargos);
            Map<Long, Cargo> cargosPorId = new HashMap<>();
            for (Cargo c : todosLosCargos) cargosPorId.put(c.getIdCargo(), c);
            Map<String, Responsable> responsablesCache = cargarResponsablesEnCache();

            // A qué persona va cada fila: no mueve lo ya enlazado, sigue las unificaciones,
            // reconoce C.I. falsos y nombres que perdieron la Ñ (ver ResolutorPersonaSync).
            Set<String> ciInvalidos = responsableGestionService.ciInvalidos();
            ResolutorPersonaSync personas = new ResolutorPersonaSync(personaService.findAll(), ciInvalidos,
                    personaService::save, this::procesarNombreCompleto);

            // Responsables con un cambio del SCIAF que no llegó a RESP.DBF (la última orden está
            // ENCOLADA o en ERROR): el DBF trae el dato viejo, así que esta pasada no los toca,
            // ni por su clave nueva ni por la vieja (si la orden cambiaba oficina o código).
            Set<Long> conOrdenPendiente = new HashSet<>();
            Set<String> clavesDbfPendientes = new HashSet<>();
            ordenesRespSinAplicar(conOrdenPendiente, clavesDbfPendientes);

            int inserted = 0, updated = 0, skipped = 0;
            int sinOficina = 0, cargosCreados = 0;
            Set<String> cargosConEnieVieja = new java.util.TreeSet<>();
            int camposNulos = 0;
            int duplicadosDbf = 0;
            int omitidosPorCola = 0;

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

                if (clavesDbfPendientes.contains(claveDbf(f.getEntidadCodigo(), f.getUnidad(),
                        String.valueOf(f.getCodOfi()), f.getCodResp()))) {
                    omitidosPorCola++;
                    continue;
                }

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

                String claveResponsable = oficina.getIdOficina() + "|" +
                    (f.getCodResp() != null ? f.getCodResp().trim() : "NULL");

                Responsable responsable = responsablesCache.get(claveResponsable);

                if (responsable != null && conOrdenPendiente.contains(responsable.getIdResponsable())) {
                    omitidosPorCola++;
                    continue;
                }
                // Marcada pendiente (su último envío no llegó, p. ej. falló al aplicar un cambio de
                // Personas y cargos) y el DBF dice otra cosa: el dato bueno es el del SCIAF. No se
                // pisa; se reenvía desde Responsables. Si coincide, se desmarca como siempre.
                if (responsable != null && responsable.isPendienteDbf() && !coincideConDbf(responsable, f)) {
                    omitidosPorCola++;
                    continue;
                }

                // getIdPersona() de un proxy no lo inicializa: no hay consulta por fila.
                Long idPersonaActual = responsable != null && responsable.getPersona() != null
                        ? responsable.getPersona().getIdPersona() : null;
                // La Ñ tecleada en el VSIAF viene en código DOS y en CP1252 se lee «¥». Se repone
                // aquí y no en JavaDbfService: upsertResponsable relee y reescribe RESP.DBF entero
                // con ese lector, y cambiaría los bytes de filas que nadie tocó.
                String nombreDbf = TextoVsiaf.repararEnieDos(f.getNombre());
                String cargoDbf = TextoVsiaf.repararEnieDos(f.getCargo());
                // ¿Le cambiaron el C.I. en el VSIAF desde la pasada anterior? Entonces la fila sigue
                // a ese C.I. (fase 8). Sin lectura anterior (null) no se sabe: no se mueve nada.
                String ciLeido = f.getCi() == null ? "" : f.getCi().trim();
                String ciAnterior = responsable != null ? responsable.getCiDbf() : null;
                boolean ciRecienEscrito = ciAnterior != null && !ciLeido.isEmpty()
                        && !Objects.equals(IdentidadPersona.numeroCi(ciAnterior, ciInvalidos),
                                           IdentidadPersona.numeroCi(ciLeido, ciInvalidos));
                Persona persona = personas.resolver(nombreDbf, f.getCi(), idPersonaActual, ciRecienEscrito);

                Cargo cargo = null;
                if (cargoDbf != null && !cargoDbf.isBlank()) {
                    String keyCargo = cargoDbf.toUpperCase().trim();
                    cargo = cargosCache.get(keyCargo);
                    if (cargo == null && keyCargo.indexOf('Ñ') >= 0) {
                        // Cargo creado por la sync vieja con la Ñ leída como «¥» (o «¤» si era ñ):
                        // se usa ese mismo, sin renombrarlo (la corrección la hace un usuario en
                        // el módulo de cargos). Así no nace un cargo duplicado.
                        cargo = cargosCache.get(keyCargo.replace('Ñ', '¥'));
                        if (cargo == null) cargo = cargosCache.get(keyCargo.replace('Ñ', '¤'));
                        if (cargo != null) cargosConEnieVieja.add(cargo.getNombre());
                    }
                    if (cargo == null) {
                        cargo = new Cargo();
                        cargo.setNombre(cargoDbf.trim());
                        cargo.setEstado("ACTIVO");
                        cargo = cargoService.save(cargo);
                        cargosCache.put(keyCargo, cargo);
                        cargosPorId.put(cargo.getIdCargo(), cargo);
                        cargosCreados++;
                    }
                    cargo = seguirFusion(cargo, cargosPorId);
                }

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
                
                String observ = TextoVsiaf.repararEnieDos(f.getObserv());
                if (observ != null && "(memo)".equalsIgnoreCase(observ.trim())) observ = null;
                responsable.setObserv(observ);
                
                responsable.setFechaUlt(f.getFechaUlt());
                responsable.setUsuario(f.getUsuario());
                responsable.setCodExp(f.getCodExp());
                responsable.setApiEstado(f.getApiEstado());
                // API_ESTADO 3 = inactivo en el VSIAF: aquí queda deshabilitado (fase 9).
                String estadoAntes = responsable.getEstado();
                responsable.setEstado(f.getApiEstado() != null
                        && f.getApiEstado() == ResponsableGestionService.API_INACTIVO
                        ? ResponsableGestionService.ESTADO_INACTIVO : "ACTIVO");
                boolean estadoCambio = !Objects.equals(estadoAntes, responsable.getEstado());
                // Viene leído del DBF → por definición está en el VSIAF: no pendiente.
                responsable.setPendienteDbf(false);

                boolean ciDbfCambio = !Objects.equals(responsable.getCiDbf(), ciLeido);
                responsable.setCiDbf(ciLeido);

                String nuevoHash = responsable.calcularHash();
                if (!esNuevo && !forzarCompleto && !ciDbfCambio && !estadoCambio) {
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
            response.putAll(personas.contadores());
            response.put("cargosCreados", cargosCreados);
            response.put("sugerenciasCargoEnie", cargosConEnieVieja.size());
            response.put("omitidosPorColaPendiente", omitidosPorCola);
            response.put("duplicadosDbf", duplicadosDbf);
            log.info("Sync RESP: {}", response);
            // Correcciones que la sync NO hace sola: las aplica un usuario desde el módulo.
            personas.sugerencias().forEach(sg -> log.info("Sync RESP sugerencia · {}", sg));
            cargosConEnieVieja.forEach(c -> log.info("Sync RESP sugerencia · CARGO_ENIE · «{}»", c));
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("Error sync", ex);
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/api/personas/buscar-por-ci")
    @ValidarUsuarioAutenticado
    @ResponseBody
    public ResponseEntity<?> buscarPersonaApi(@RequestParam String ci,
                                              @RequestParam(required = false) String idEnc) {
        // Por número de C.I. (sin extensión) y sin reventar si hay dos personas con el mismo:
        // devuelve la más antigua, su cargo y sus oficinas (Modificar ofrece cargar sus datos).
        Long idResponsable = null;
        if (idEnc != null && !idEnc.isBlank()) {
            try { idResponsable = Long.parseLong(Encriptar.decrypt(idEnc)); } catch (Exception e) { /* sin fila: solo busca */ }
        }
        Map<String, Object> p = responsableGestionService.fichaPorCi(ci, idResponsable);
        return p != null ? ResponseEntity.ok(p) : ResponseEntity.notFound().build();
    }

    @GetMapping("/api/cargos/search")
    @ResponseBody
    public List<Map<String, String>> buscarCargos(@RequestParam(required = false) String q) {
        return cargoService.nombresParecidos(q, 20).stream()
            .map(n -> Map.of("nombre", n))
            .collect(Collectors.toList());
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

    private Map<String, Cargo> cargarCargosEnCache(List<Cargo> todos) {
        
        return todos.stream()
            .collect(Collectors.toMap(
                c -> (c.getNombre() != null ? c.getNombre().toUpperCase().trim() : ""),
                c -> c,
                // Mismo nombre en dos cargos (p. ej. el vigente y uno juntado en él): gana el
                // vigente; si no, el primero. El FUSIONADO se sigue luego con seguirFusion.
                // (sin estado cuenta como vigente; ELIMINADO / FUSIONADO pierden).
                (existing, replacement) -> !vigente(existing) && vigente(replacement) ? replacement : existing,
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
            String quien = u != null ? u.getUsuario() : "SISTEMA";
            // Si su oficina también quedó pendiente (alta rápida desde Donaciones), va primero
            // ella: si no, el VSIAF recibe un responsable en una oficina que no existe allá.
            List<String> avisosOficina = vsiafApoyoService.asegurarEnVsiaf(resp.getOficina(), null, quien);
            VsiafApoyoService.Envio envio = vsiafApoyoService.reenviarResponsable(resp, quien);
            if (!avisosOficina.isEmpty()) {
                envio = new VsiafApoyoService.Envio(false, String.join(" ", avisosOficina) + " " + envio.mensaje());
            }
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

    /**
     * Deshabilita (API_ESTADO 3) o habilita (1) al responsable, en el SCIAF y en el VSIAF.
     * Deshabilitar solo sin bienes a cargo. Sin rol administrativo va como solicitud con motivo.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/estado/{id}")
    @ResponseBody
    public ResponseEntity<?> cambiarEstadoResponsable(@PathVariable("id") String idEnc, HttpServletRequest request,
            @RequestParam boolean habilitar, @RequestParam(required = false) String motivo) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            Responsable resp = responsableGestionService.buscar(id);
            if (!habilitar) {
                String impide = responsableGestionService.impideDeshabilitar(resp);
                if (impide != null) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", impide));
            }
            if (!esAdmin(request)) {
                autorizacionService.solicitar(ResponsableGestionService.TIPO_ESTADO, ActividadService.MOD_RESPONSABLE,
                        id, ResponsableGestionService.referencia(resp), habilitar ? "Habilitar al responsable" : "Deshabilitar al responsable",
                        Map.of("idResponsable", id, "habilitar", habilitar), motivo, usuario);
                return ResponseEntity.ok(Map.of("ok", true, "solicitud", true,
                        "msg", "Solicitud enviada. Se aplicará cuando el revisor la apruebe."));
            }
            ResponsableGestionService.Resultado r = responsableGestionService.cambiarEstado(id, habilitar, usuario);
            return ResponseEntity.ok(Map.of("ok", true, "vsiafOk", r.vsiafOk(), "msg", r.msg()));
        } catch (IllegalArgumentException | IllegalStateException invalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", invalido.getMessage()));
        } catch (Exception e) {
            log.error("Error cambiando el estado del responsable", e);
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

    /** Conserva la Ñ: antes la cambiaba por N y la sync guardaba ACUNA / MUNOZ. */
    private String limpiarNombre(String nombre) {
        return IdentidadPersona.normalizarNombre(nombre);
    }

    /**
     * Responsables cuya última orden a RESP no quedó OK (ENCOLADA o ERROR), y las claves del
     * DBF que esas órdenes buscan (la clave vieja, si la orden cambiaba oficina o código).
     */
    private void ordenesRespSinAplicar(Set<Long> ids, Set<String> clavesDbf) {
        Map<Long, DbfColaOrden> ultima = new HashMap<>();
        for (DbfColaOrden o : colaOrdenDao.findByTablaAndIdRegistroIsNotNullOrderByIdOrdenAsc("RESP")) {
            ultima.put(o.getIdRegistro(), o);
        }
        for (DbfColaOrden o : ultima.values()) {
            if (DbfColaOrden.ENCOLADA.equals(o.getEstado()) || DbfColaOrden.ERROR.equals(o.getEstado())) {
                ids.add(o.getIdRegistro());
                Map<String, String> c = new HashMap<>();
                // Formato de DbfColaRegistroService: "ENTIDAD=148 UNIDAD=CULP CODOFIC=323 CODRESP=2".
                if (o.getClave() != null) {
                    for (String par : o.getClave().trim().split("\\s+")) {
                        int eq = par.indexOf('=');
                        if (eq > 0) c.put(par.substring(0, eq), par.substring(eq + 1));
                    }
                }
                if (c.containsKey("CODOFIC") && c.containsKey("CODRESP")) {
                    clavesDbf.add(claveDbf(c.get("ENTIDAD"), c.get("UNIDAD"), c.get("CODOFIC"), c.get("CODRESP")));
                }
            }
        }
    }

    private static String claveDbf(String entidad, String unidad, String codOfic, String codResp) {
        return String.join("|", nvl(entidad).trim(), nvl(unidad).trim().toUpperCase(),
                soloNumero(codOfic), soloNumero(codResp));
    }

    private static String soloNumero(String s) {
        String d = nvl(s).replaceAll("\\D", "");
        return d.isEmpty() ? "" : String.valueOf(Long.parseLong(d));
    }

    /** ¿RESP.DBF ya tiene lo que el SCIAF quiere escribir en esta fila (nombre, C.I., cargo)? */
    private static boolean coincideConDbf(Responsable r, com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ResponsableDbf f) {
        Persona p = r.getPersona();
        String nomresp = p == null ? "" : com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.nomresp(
                new com.usic.SistemasActivosFijosUAP.model.service.personas.PlanPersona.DatosPersona(
                        p.getIdPersona(), p.getNombre(), p.getPaterno(), p.getMaterno(), null, null, false));
        boolean nombre = IdentidadPersona.normalizarNombre(nomresp)
                .equals(IdentidadPersona.normalizarNombre(TextoVsiaf.repararEnieDos(f.getNombre())));
        boolean ci = nvl(p == null ? null : p.getCi()).trim().equalsIgnoreCase(nvl(f.getCi()).trim());
        String cargoSciaf = r.getCargo() == null ? "" : nvl(r.getCargo().getNombre());
        boolean cargo = cargoSciaf.trim().equalsIgnoreCase(nvl(TextoVsiaf.repararEnieDos(f.getCargo())).trim());
        return nombre && ci && cargo;
    }

    private static boolean vigente(Cargo c) {
        return c.getEstado() == null || "ACTIVO".equals(c.getEstado());
    }

    /** Un cargo normalizado en el módulo de personas y cargos apunta al que quedó. */
    private static Cargo seguirFusion(Cargo c, Map<Long, Cargo> porId) {
        int saltos = 0;
        while (c != null && "FUSIONADO".equals(c.getEstado()) && c.getFusionadoEn() != null && saltos++ < 10) {
            Cargo destino = porId.get(c.getFusionadoEn());
            if (destino == null) break;
            c = destino;
        }
        return c;
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static LocalDateTime aLocal(Date d) {
        return LocalDateTime.ofInstant(d.toInstant(), java.time.ZoneId.systemDefault());
    }
}