package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.AuxiliarDbfWriterService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.OficinaDbfWriterService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.RespDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IDetalleAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dao.IHistorialActivoDao;
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaDao;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoFormDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoPendienteItemDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.AsignacionPendienteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.DataTablesResponse;
import com.usic.SistemasActivosFijosUAP.model.dto.DetalleActivoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.DetalleRegistroItem;
import com.usic.SistemasActivosFijosUAP.model.dto.EditarActivoPendienteRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.EditarLoteRequest;
import com.usic.SistemasActivosFijosUAP.componet.SseEmitterRegistry;
import com.usic.SistemasActivosFijosUAP.model.dto.RegistroHuecoRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.RegistroHuecosLoteRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.RegistroMasivoRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.HistorialActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;
import com.usic.SistemasActivosFijosUAP.model.service.ActivoSyncService;
import com.usic.SistemasActivosFijosUAP.model.service.TransferenciaService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/administracion/activo")
@RequiredArgsConstructor
public class ActivosController {
    private final IActivoService activoService;
    private final FuncionesActivoRepo funciones;
    private final IMunicipioService municipioService;
    private final IPredioServicio predioServicio;
    private final IGrupoContableService grupoContableService;
    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;
    private final IOrganismoFinancieroService organismoFinancieroService;
    private final IAuxiliarService auxiliarService;
    private final IEstadoActivoService estadoActivoService;

    private final ActualDbfWriterService actualDbfWriterService;
    private final AuxiliarDbfWriterService auxiliarDbfWriterService;
    private final OficinaDbfWriterService oficinaDbfWriterService;
    private final RespDbfWriterService respDbfWriterService;

    private final IConfiguracionGestionService configuracionGestionService;
    private final IAsignacionActivoService asignacionActivoService;
    private final IDetalleAsignacionActivoService detalleAsignacionActivoService;

    private final TransferenciaService transferenciaService;
    private final ITransferenciaDao transferenciaDao;

    private final ActivoSyncService activoSyncService;
    private final SseEmitterRegistry sseRegistry;

    private final PasswordEncoder passwordEncoder;
    private final IHistorialActivoDao historialActivoDao;

    /**
     * Código del permiso (opcion_menu oculto) que habilita el cambio urgente del
     * código de un activo. Un ADMINISTRADOR / SUPER USUARIO lo otorga desde la
     * pantalla de permisos por usuario. Ver {@code OpcionMenuSeeder.PERMISOS}.
     */
    private static final String PERMISO_EDITAR_CODIGO = "opcion_activo_editar_codigo";

    @PersistenceContext
    private EntityManager entityManager;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String inicio_oficina() {
        return "activo/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros")
    public String tablaRegistros_activo(Model model) throws Exception {
        List<Activo> listasOficinas = activoService.listarActivos();
        List<String> encryptedIds = new ArrayList<>();
        for (Activo oficinas : listasOficinas) {
            String id_encryptado = Encriptar.encrypt(Long.toString(oficinas.getIdActivo()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listasOficinas", listasOficinas);
        model.addAttribute("id_encryptado", encryptedIds);
        return "activo/tabla_registro";
    }

    @PostMapping("/datatables")
    @ResponseBody
    @Transactional(readOnly = true)
    public DataTablesResponse<ActivoDTO> listarActivosDatatables(@RequestParam Map<String, String> params) {
        int start = Integer.parseInt(params.get("start"));
        int length = Integer.parseInt(params.get("length"));
        String searchValue = params.get("search[value]");

        String codigo = params.get("codigo");
        String responsableId = params.get("responsable");
        String oficinaId = params.get("oficina");
        String predioId = params.get("predio");
        String usuario = params.get("usuario");
        String fecha = params.get("fecha");

        PageRequest pageRequest = PageRequest.of(start / length, length);

        Page<Activo> pagina = activoService.buscarConFiltros(
                searchValue, codigo, responsableId, oficinaId, predioId, usuario, fecha, pageRequest);

        List<ActivoDTO> activosDTO = pagina.getContent().stream().map(activo -> {
            ActivoDTO dto = new ActivoDTO();
            dto.setIndex("");
            dto.setCodigo(activo.getCodigo());
            dto.setDescripcion(activo.getDescripcion());
            dto.setResponsable(activo.getResponsable().getPersona().getNombre() + " "
                    + activo.getResponsable().getPersona().getPaterno() + " "
                    + activo.getResponsable().getPersona().getMaterno());
            dto.setOficina(activo.getOficina().getNombre());
            dto.setCosto(activo.getCosto());
            dto.setVidaUtil(activo.getVidaUtil());
            dto.setFechaAdquisicion(activo.getFechaAdquisicion().toString());
            dto.setEstado(activo.getEstadoActivo().getNombre());

            try {
                String idEncriptado = Encriptar.encrypt(activo.getIdActivo().toString());
                dto.setAcciones(
                        " <button class='btn btn-sm btn-danger' onclick=\"eliminar('" + activo.getNombre() + "', '"
                                + idEncriptado + "')\">Eliminar</button>");
            } catch (Exception e) {
                dto.setAcciones("<span class='text-danger'>Error al generar acciones</span>");
                e.printStackTrace();
            }

            return dto;
        }).toList();

        return new DataTablesResponse<>(pagina.getTotalElements(), pagina.getTotalElements(), activosDTO);
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/formulario")
    public String formulario_activo(Model model, Activo activo, HttpServletRequest request) {
        model.addAttribute("municipios", municipioService.findAll());
        model.addAttribute("predios", predioServicio.findAll());
        model.addAttribute("grupos", grupoContableService.listarGruposContables());
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        model.addAttribute("responsables", responsableService.listarResponsables());
        model.addAttribute("financiadores", organismoFinancieroService.findAll());
        model.addAttribute("auxiliares", auxiliarService.findAll());
        // Habilita (o no) el botón de "modificar código urgente" según el permiso del usuario.
        model.addAttribute("puedeEditarCodigo", tienePermisoEditarCodigo(request));
        return "activo/formulario";
    }

    /**
     * ¿El usuario en sesión puede ejecutar el cambio urgente de código de un activo?
     * Se apoya en {@code session.opciones} (los permisos efectivos calculados al
     * login). ADMINISTRADOR ya tiene todos los códigos; a otros roles (p. ej. APOYO)
     * se les debe otorgar explícitamente {@link #PERMISO_EDITAR_CODIGO}.
     */
    private boolean tienePermisoEditarCodigo(HttpServletRequest request) {
        if (request == null || request.getSession(false) == null) return false;
        Object opciones = request.getSession().getAttribute("opciones");
        if (opciones instanceof Set<?> set) {
            return set.contains(PERMISO_EDITAR_CODIGO);
        }
        return false;
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/formulario-edit/{id_activo}")
    public String formularioEdit_activo(Model model, @PathVariable("id_activo") String idActivo) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idActivo));
        model.addAttribute("activo", activoService.findById(id));
        model.addAttribute("edit", "true");
        return "activo/formulario";
    }

    @ValidarUsuarioAutenticado
    @PostMapping(value = "/registrar-activo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> registrar_activo(
            HttpServletRequest request,
            @Validated @ModelAttribute Activo activo,
            @RequestParam(defaultValue = "1") Integer cantidad,
            BindingResult br) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");

        if (br.hasErrors()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("errors", br.getFieldErrors().stream()
                    .map(fe -> Map.of(
                            "field", fe.getField(),
                            "rejectedValue", fe.getRejectedValue(),
                            "message", fe.getDefaultMessage()))
                    .toList());
            log.warn("Errores de binding en registrar_activo: {}", err);
            return ResponseEntity.badRequest().body(err);
        }

        if (cantidad < 1) cantidad = 1;
        if (cantidad > 100) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Máximo 100 activos por lote."));

        log.info("Iniciando Registro Masivo: {} activos. Código Base: {}", cantidad, activo.getCodigo());

        List<String> codigosGenerados = new ArrayList<>();
        String codigoActualStr = activo.getCodigo();

        List<String> idsReporte = new ArrayList<>();
        List<Activo> activosGuardados = new ArrayList<>();

        try {
            // El auxiliar llega del @ModelAttribute con sólo el id: hay que traerlo completo
            // y comprobar que sea del predio de la oficina y del grupo contable del activo.
            // Si no, el CODAUX que se escriba en el VSIAF apunta a otro auxiliar (o a nada).
            Oficina oficinaActivo = (activo.getOficina() != null && activo.getOficina().getIdOficina() != null)
                    ? oficinaService.findById(activo.getOficina().getIdOficina())
                    : activo.getOficina();
            GrupoContable grupoActivo = (activo.getGrupoContable() != null && activo.getGrupoContable().getIdGrupoContable() != null)
                    ? grupoContableService.findById(activo.getGrupoContable().getIdGrupoContable())
                    : activo.getGrupoContable();
            Auxiliar auxiliarActivo = resolverAuxiliarCoherente(
                    activo.getAuxiliar() != null ? activo.getAuxiliar().getIdAuxiliar() : null,
                    oficinaActivo, grupoActivo);

            for (int i = 0; i < cantidad; i++) {

                Activo nuevoActivo = new Activo();
                
                nuevoActivo.setDescripcion(activo.getDescripcion());
                nuevoActivo.setCosto(activo.getCosto());
                nuevoActivo.setVidaUtil(activo.getVidaUtil());
                nuevoActivo.setFechaAdquisicion(activo.getFechaAdquisicion());
                nuevoActivo.setObserv(activo.getObserv());
                
                nuevoActivo.setGrupoContable(grupoActivo);
                nuevoActivo.setOficina(oficinaActivo);
                nuevoActivo.setResponsable(activo.getResponsable());
                nuevoActivo.setOrganismoFinanciero(activo.getOrganismoFinanciero());
                nuevoActivo.setAuxiliar(auxiliarActivo);
                nuevoActivo.setEstadoActivo(activo.getEstadoActivo());

                String codigoParaEste = (i == 0) 
                    ? codigoActualStr 
                    : incrementarCodigoString(codigoActualStr, i);
                
                nuevoActivo.setCodigo(codigoParaEste);
                
                nuevoActivo.setUsuario(usuario.getUsuario());
                nuevoActivo.setFecMod(LocalDate.now());
                nuevoActivo.setFechaUlt(LocalDate.now());
                
                nuevoActivo.setApiEstado(Short.valueOf("3"));
                nuevoActivo.setCostoAnterior(0.0);
                nuevoActivo.setDepreciacionAcum(0.0);
                nuevoActivo.setVidaUtilAnterior(0);
                
                if(nuevoActivo.getOrganismoFinanciero() != null) {
                     OrganismoFinanciero of = organismoFinancieroService.findById(nuevoActivo.getOrganismoFinanciero().getIdOrganismoFinanciero());
                     nuevoActivo.setOrganismoFinanciero(of);
                     nuevoActivo.setOrgFinCode(of.getCodOf());
                }
                
                if (nuevoActivo.getEstadoActivo() == null) {
                    nuevoActivo.setEstadoActivo(estadoActivoService.findById(1L));
                }
            
                nuevoActivo.setEstado("PENDIENTE");

                activoService.save(nuevoActivo);
                codigosGenerados.add(nuevoActivo.getCodigo());
                activosGuardados.add(nuevoActivo);
            }

            for (Activo a : activosGuardados) {
                try {
                    idsReporte.add(Encriptar.encrypt(String.valueOf(a.getIdActivo())));
                } catch (Exception e) { /* ignorar */ }
            }

            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("ok", true);
            ok.put("msg", String.format("Se registraron %d activos correctamente...", cantidad));
            ok.put("idsParaReporte", idsReporte);

            return ResponseEntity.ok(ok);
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("Error en registro masivo", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    private String incrementarCodigoString(String codigoBase, int incremento) {
        try {

            int lastDash = codigoBase.lastIndexOf('-');
            if (lastDash == -1) return codigoBase + "-" + incremento;

            String prefix = codigoBase.substring(0, lastDash + 1);
            String numberPart = codigoBase.substring(lastDash + 1);
            
            long numero = Long.parseLong(numberPart);
            long nuevoNumero = numero + incremento;
            
            String formato = "%0" + numberPart.length() + "d";
            return prefix + String.format(formato, nuevoNumero);
            
        } catch (Exception e) {
            log.error("No se pudo incrementar código: " + codigoBase);
            return codigoBase + "-" + incremento;
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-masivo")
    public ResponseEntity<?> registrarMasivo(@RequestBody RegistroMasivoRequest request, HttpServletRequest httpReq) {
        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
        
        try {

            if (request.getItems() == null || request.getItems().isEmpty()) return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Debe agregar al menos un ítem."));

            OrganismoFinanciero orgFinGlobal = (request.getIdOrganismoFinanciero() != null) ? organismoFinancieroService.findById(request.getIdOrganismoFinanciero()) : null;

            List<String> idsReporte = new ArrayList<>();
            int totalCreados = 0;

            Map<String, Long> correlativosActuales = new HashMap<>();

            for (DetalleRegistroItem item : request.getItems()) {

                if (item.getIdResponsable() == null) {
                    return ResponseEntity.badRequest().body(
                        Map.of("ok", false, "msg", "Falta responsable en uno de los ítems."));
                }
                Responsable responsable = responsableService.findById(item.getIdResponsable());
                
                GrupoContable grupo = grupoContableService.findById(item.getIdGrupoContable());
                Oficina oficina = oficinaService.findById(item.getIdOficina());
                // El auxiliar tiene que ser del predio de la oficina y del grupo del ítem:
                // el CODAUX sólo significa algo dentro de esa combinación.
                Auxiliar auxiliar = resolverAuxiliarCoherente(item.getIdAuxiliar(), oficina, grupo);
                String codMun = oficina.getPredio().getMunicipio().getCodigo();
                String codPred = oficina.getPredio().getCodigo();
                String codGrup = String.format("%02d", grupo.getCodDbf()); 
                String keyMap = codMun + "-" + codPred + "-" + codGrup;

                if (!correlativosActuales.containsKey(keyMap)) {
                    String codigoBaseBd = funciones.previewCodigoPorCodes(codMun, codPred, codGrup);
                    long baseInicial = extraerNumeroCorrelativo(codigoBaseBd);
                    correlativosActuales.put(keyMap, baseInicial);
                }
                
                long correlativoActual = correlativosActuales.get(keyMap); 

                for (DetalleActivoDTO detalle : item.getDetalles()) {
                    Activo a = new Activo();
                    
                    String codigoFinal = construirCodigo(codMun, codPred, codGrup, correlativoActual);
                    correlativoActual++;

                    a.setCodigo(codigoFinal);

                   String descBase = (detalle.getDescripcion() != null && !detalle.getDescripcion().trim().isEmpty()) 
                        ? detalle.getDescripcion().trim() 
                        : item.getDescripcion().trim();

                    a.setDescripcion(construirDescripcionActivo(
                            descBase, detalle.getColor(), detalle.getMarca(),
                            detalle.getModelo(), detalle.getSerie(), item.isIncluyeAccesorio()));

                    a.setFechaAdquisicion(request.getFechaAdquisicion());
                    a.setVidaUtil(item.getVidaUtil() != null ? BigDecimal.valueOf(item.getVidaUtil()) : BigDecimal.ZERO);
                    a.setCosto(item.getCosto() != null ? item.getCosto() : 0.0);
                    a.setResponsable(responsable);
                    a.setOrganismoFinanciero(orgFinGlobal);
                    if(orgFinGlobal != null) a.setOrgFinCode(orgFinGlobal.getCodOf());
                    a.setGrupoContable(grupo);
                    a.setOficina(oficina);
                    a.setAuxiliar(auxiliar);
                    a.setEstado("PENDIENTE");
                    a.setApiEstado(Short.valueOf("3"));
                    a.setVidaUtilAnterior(0);
                    // a.setBandUfv(); no se sabe aun, se guarda en null
                    a.setEstadoActivo(estadoActivoService.findById(1L));
                    a.setCostoAnterior(0.0);
                    a.setDepreciacionAcum(0.0);
                    a.setUsuario(usuarioNombre);
                    a.setFecMod(LocalDate.now());
                    a.setFechaUlt(LocalDate.now());
                    
                    activoService.save(a);
                    idsReporte.add(Encriptar.encrypt(String.valueOf(a.getIdActivo())));
                    totalCreados++;
                }

                correlativosActuales.put(keyMap, correlativoActual);
            }

            notificarCambioPendientes("registro");
            return ResponseEntity.ok(Map.of(
                "ok", true,
                "msg", "Se registraron " + totalCreados + " activos correctamente.",
                "idsParaReporte", idsReporte
            ));

        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("Error masivo", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    private long extraerNumeroCorrelativo(String codigoCompleto) {
        try {
            String[] partes = codigoCompleto.split("-");
            return Long.parseLong(partes[partes.length - 1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private String construirCodigo(String mun, String pred, String grup, long numero) {
        return String.format("%s-%s-%s-%05d", mun, pred, grup, numero);
    }

    /**
     * Arma la descripción final del activo en el orden acordado:
     * DESCRIPCIÓN + COLOR + M(arca) + MOD(elo) + S(erie).
     * El COLOR se separa con espacio; marca/modelo/serie forman un bloque
     * separado por comas con etiquetas {@code M:}, {@code MOD:} y {@code S:}.
     * Ej: {@code AIRE ACONDICIONADO COLOR BLANCO M:IKA, MOD:AA-36FJ 36000 BTU/H, S:B2507E154702N0008}
     */
    private String construirDescripcionActivo(String base, String color, String marca, String modelo, String serie, boolean incluyeAccesorio) {
        StringBuilder sb = new StringBuilder(base != null ? base.trim() : "");
        if (color != null && !color.trim().isEmpty()) {
            sb.append(" COLOR ").append(color.trim());
        }
        List<String> tecnicos = new ArrayList<>();
        if (marca  != null && !marca.trim().isEmpty())  tecnicos.add("M:" + marca.trim());
        if (modelo != null && !modelo.trim().isEmpty()) tecnicos.add("MOD:" + modelo.trim());
        if (serie  != null && !serie.trim().isEmpty())  tecnicos.add("N/S:" + serie.trim());
        if (!tecnicos.isEmpty()) {
            sb.append(" ").append(String.join(", ", tecnicos));
        }
        if (incluyeAccesorio) {
            sb.append(" INCLUYE ACCESORIO");
        }
        return sb.toString().toUpperCase();
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) {
            s = s.trim();
            if (s.isEmpty()) return null;
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private Integer toInteger(Object value, Integer defaultValue) {
        Long l = toLong(value);
        return l != null ? l.intValue() : defaultValue;
    }

    private Double toDouble(Object value, Double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            s = s.trim();
            if (s.isEmpty()) return defaultValue;
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    /**
     * Resuelve el auxiliar de un activo verificando que <b>pertenezca al mismo predio y
     * grupo contable</b> que el activo.
     * <p>
     * En el VSIAF el auxiliar no se identifica por un id: se identifica por la tupla
     * (ENTIDAD, UNIDAD, CODCONT, CODAUX). El CODAUX es un correlativo <i>dentro</i> de un
     * predio y un grupo contable — el auxiliar 3 del predio CAUN en el grupo 12 no tiene
     * nada que ver con el auxiliar 3 de otro predio, ni con el 3 de otro grupo en el mismo
     * predio. Si se guarda en el activo un auxiliar de otro ámbito, en ACTUAL.DBF queda un
     * CODAUX que apunta a otra cosa (o a nada) y el VSIAF muestra el activo sin auxiliar.
     * <p>
     * Por eso se valida acá y no sólo en el front: el select se arma por AJAX y basta con
     * que el usuario cambie el grupo o la oficina después de elegir el auxiliar para que
     * llegue un id que ya no corresponde.
     *
     * @return el auxiliar completo, o {@code null} si {@code idAuxiliar} es null (es opcional)
     * @throws IllegalArgumentException si el auxiliar no existe o no es del predio/grupo del activo
     */
    private Auxiliar resolverAuxiliarCoherente(Long idAuxiliar, Oficina oficina, GrupoContable grupo) {
        if (idAuxiliar == null) return null;

        Auxiliar aux = auxiliarService.findById(idAuxiliar);
        if (aux == null) {
            throw new IllegalArgumentException("El auxiliar seleccionado no existe (id=" + idAuxiliar + ").");
        }
        if ("ELIMINADO".equalsIgnoreCase(aux.getEstado())) {
            throw new IllegalArgumentException("El auxiliar '" + aux.getNombre() + "' está dado de baja.");
        }

        if (grupo != null && aux.getGrupoContable() != null
                && !aux.getGrupoContable().getIdGrupoContable().equals(grupo.getIdGrupoContable())) {
            throw new IllegalArgumentException(String.format(
                "El auxiliar '%s' es del grupo contable %s y el activo es del grupo %s. "
              + "Los auxiliares son propios de cada grupo contable: elegí uno de la lista del grupo del activo.",
                aux.getNombre(),
                aux.getGrupoContable().getCodContable(),
                grupo.getCodContable()));
        }

        Predio predioActivo = (oficina != null) ? oficina.getPredio() : null;
        if (predioActivo != null && aux.getPredio() != null
                && !aux.getPredio().getIdPredio().equals(predioActivo.getIdPredio())) {
            throw new IllegalArgumentException(String.format(
                "El auxiliar '%s' pertenece al predio %s y el activo está en el predio %s. "
              + "Cada predio tiene su propia lista de auxiliares: elegí uno del predio de la oficina.",
                aux.getNombre(),
                aux.getPredio().getUnidad(),
                predioActivo.getUnidad()));
        }

        return aux;
    }

    /**
     * Deja el auxiliar del activo en el VSIAF antes de mandar el activo.
     * <p>
     * El orden importa: ACTUAL.DBF guarda el CODAUX, no una referencia. Si el activo llega
     * antes que su auxiliar, en el VSIAF queda apuntando a un auxiliar inexistente y el
     * activo se ve "sin auxiliar". El INSERT que encola el worker es idempotente (inserta
     * sólo si no existe la clave), así que llamarlo por cada activo no duplica nada.
     *
     * @return null si salió bien o no había auxiliar; el motivo del fallo si no se pudo
     */
    private String sincronizarAuxiliarDelActivo(Activo a, String usuarioNombre) {
        if (a == null || a.getAuxiliar() == null) return null;
        try {
            auxiliarDbfWriterService.asegurarEnVsiaf(a.getAuxiliar(), usuarioNombre);
            return null;
        } catch (Exception e) {
            log.error("[AUX-VSIAF] No se pudo enviar el auxiliar del activo {}: {}", a.getCodigo(), e.getMessage());
            return e.getMessage();
        }
    }

    /**
     * Campos obligatorios que un activo debe tener completos antes de poder
     * asignarle un documento (PREV) o subirlo al VSIAF. El único campo opcional
     * es el Auxiliar. Costo y Vida útil deben ser mayores a 0.
     *
     * @return lista de etiquetas de los campos faltantes (vacía si está completo).
     */
    private static List<String> camposFaltantesVsiaf(Activo a) {
        List<String> f = new ArrayList<>();
        if (a == null) { f.add("Activo"); return f; }
        if (a.getOficina() == null || a.getOficina().getPredio() == null) f.add("Oficina");
        if (a.getResponsable() == null)            f.add("Responsable");
        if (a.getGrupoContable() == null)          f.add("Grupo contable");
        if (a.getOrganismoFinanciero() == null)    f.add("Financiador");
        if (a.getDescripcion() == null || a.getDescripcion().trim().isEmpty()) f.add("Descripción");
        if (a.getFechaAdquisicion() == null)       f.add("Fecha de adquisición");
        if (a.getCosto() == null || a.getCosto() <= 0) f.add("Costo");
        if (a.getVidaUtil() == null || a.getVidaUtil().signum() <= 0) f.add("Vida útil");
        return f;
    }

    /**
     * Avisa por SSE a todas las pestañas conectadas que la lista de pendientes
     * cambió, para que refresquen en vivo. Nunca propaga errores al flujo principal.
     */
    private void notificarCambioPendientes(String tipo) {
        try {
            sseRegistry.broadcast("pendientes-cambio",
                Map.of("tipo", tipo, "ts", System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("No se pudo emitir SSE pendientes-cambio: {}", e.getMessage());
        }
    }

    /* ────────────────────────────────────────────────────────────────────────────
     * Envío al VSIAF: decir lo que realmente pasó
     *
     * En modo cola, encolar no es haber escrito: la orden queda en _cola/ y la aplica
     * el worker VFPOLEDB. Hasta que ColaConfirmacionScheduler lee su respuesta, lo
     * único cierto es que el pedido salió. Antes acá se respondía "sincronizado" en el
     * mismo instante del encolado, así que un worker caído o un rechazo del DBF pasaban
     * inadvertidos y los dos sistemas quedaban distintos sin que nadie se enterara.
     * ──────────────────────────────────────────────────────────────────────────── */

    /** Marca el activo como enviado, con el estado que corresponda al modo de escritura. */
    private void marcarEnvioAlVsiaf(Activo a) {
        boolean enCola = actualDbfWriterService.esModoCola();
        a.setSincVsiaf(enCola ? Activo.SINC_EN_COLA : Activo.SINC_CONFIRMADO);
        a.setSincVsiafMensaje(null);
        a.setSincVsiafFecha(LocalDateTime.now());
    }

    /** "EN_COLA" mientras el worker no responda; "OK" cuando la escritura fue directa. */
    private String estadoEnvio() {
        return actualDbfWriterService.esModoCola() ? "EN_COLA" : "OK";
    }

    /**
     * Deja anotado en el activo que el envío al VSIAF no salió, con el motivo.
     * <p>
     * Fuera de {@code @Transactional} el {@code save} explícito es lo que hace que la
     * marca sobreviva: si no, el activo queda con el cambio en la base y sin rastro de
     * que el VSIAF nunca lo recibió.
     */
    private void marcarErrorDeEnvio(Activo a, String motivo) {
        try {
            a.setSincVsiaf(Activo.SINC_ERROR);
            a.setSincVsiafMensaje(motivo);
            a.setSincVsiafFecha(LocalDateTime.now());
            activoService.save(a);
        } catch (Exception e) {
            log.warn("No se pudo marcar el error de sincronización del activo {}: {}",
                    a.getCodigo(), e.getMessage());
        }
    }

    /** Frase para el usuario, acorde a si la escritura ya ocurrió o está en camino. */
    private String detalleEnvio() {
        return actualDbfWriterService.esModoCola()
                ? "Enviado al VSIAF: queda en cola hasta que el worker lo confirme."
                : "Escrito en el VSIAF.";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-activo")
    public ResponseEntity<?> modificar_activo(
            HttpServletRequest request,
            @Validated @ModelAttribute Activo activoForm,
            BindingResult br) {

        if (br.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "errors", br.getFieldErrors().stream()
                            .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                            .toList()));
        }

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = usuario.getUsuario();

        try {

            Activo activoOriginal = activoService.findById(activoForm.getIdActivo());
            if (activoOriginal == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "ok", false,
                        "msg", "No se encontró el activo con ID: " + activoForm.getIdActivo()));
            }

            String codigoOriginal = activoOriginal.getCodigo();
            boolean estabaActivo = "ACTIVO".equalsIgnoreCase(activoOriginal.getEstado());

            activoOriginal.setCodigo(activoForm.getCodigo());
            // El formulario no tiene campo para codigoSec (código secundario que viene del
            // VSIAF). Copiarlo tal cual del form manda null y borra el que ya tenía, en la
            // base y —vía la orden UPDATE— en ACTUAL.DBF.
            if (activoForm.getCodigoSec() != null) {
                activoOriginal.setCodigoSec(activoForm.getCodigoSec());
            }
            activoOriginal.setDescripcion(activoForm.getDescripcion());
            activoOriginal.setCosto(activoForm.getCosto());
            activoOriginal.setVidaUtil(activoForm.getVidaUtil());
            activoOriginal.setFechaAdquisicion(activoForm.getFechaAdquisicion());
            
            if (activoForm.getGrupoContable() != null && activoForm.getGrupoContable().getIdGrupoContable() != null) {
                GrupoContable grupoCompleto = grupoContableService.findById(
                    activoForm.getGrupoContable().getIdGrupoContable()
                );
                activoOriginal.setGrupoContable(grupoCompleto);
            } else {
                activoOriginal.setGrupoContable(null);
            }

            if (activoForm.getOficina() != null && activoForm.getOficina().getIdOficina() != null) {
                Oficina oficinaCompleta = oficinaService.findById(activoForm.getOficina().getIdOficina());
                activoOriginal.setOficina(oficinaCompleta);
            } else {
                activoOriginal.setOficina(null);
            }

            if (activoForm.getResponsable() != null && activoForm.getResponsable().getIdResponsable() != null) {
                Responsable responsableCompleto = responsableService.findById(
                    activoForm.getResponsable().getIdResponsable()
                );
                activoOriginal.setResponsable(responsableCompleto);
            } else {
                activoOriginal.setResponsable(null);
            }

            if (activoForm.getOrganismoFinanciero() != null && 
                activoForm.getOrganismoFinanciero().getIdOrganismoFinanciero() != null) {
                OrganismoFinanciero orgFin = organismoFinancieroService.findById(
                    activoForm.getOrganismoFinanciero().getIdOrganismoFinanciero()
                );
                activoOriginal.setOrganismoFinanciero(orgFin);
                if (orgFin != null) {
                    activoOriginal.setOrgFinCode(orgFin.getCodOf());
                }
            } else {
                activoOriginal.setOrganismoFinanciero(null);
                activoOriginal.setOrgFinCode(null);
            }

            if (activoForm.getAuxiliar() != null && activoForm.getAuxiliar().getIdAuxiliar() != null) {
                Auxiliar auxiliarCompleto = auxiliarService.findById(
                    activoForm.getAuxiliar().getIdAuxiliar()
                );
                activoOriginal.setAuxiliar(auxiliarCompleto);
            } else {
                activoOriginal.setAuxiliar(null);
            }

            EstadoActivo estadoActivo = estadoActivoService.findById(1L);
            activoOriginal.setEstadoActivo(estadoActivo);
            activoOriginal.setFecMod(LocalDate.now());
            activoOriginal.setUsuMod(usuarioNombre);
            activoOriginal.setEstado("ACTIVO");
            activoOriginal.setModificacionIdUsuario(usuario.getIdUsuario());
            activoOriginal.setModificacion(new Date());
            activoOriginal.setUsuario(usuarioNombre);
            activoOriginal.setEstadoActivo(activoOriginal.getEstadoActivo());
            activoOriginal.setVidaUtilAnterior(0);
            activoOriginal.setFechaUlt(LocalDate.now());
            activoOriginal.setFecMod(LocalDate.now());
            activoOriginal.setCostoAnterior(Double.valueOf(0));
            activoOriginal.setApiEstado(Short.valueOf("3"));
            activoService.save(activoOriginal);
            log.info("Activo {} actualizado en BD.", activoOriginal.getCodigo());

            if (estabaActivo) {
                try {

                    if (activoOriginal.getOficina() == null || 
                        activoOriginal.getOficina().getPredio() == null ||
                        activoOriginal.getOficina().getPredio().getEntidad() == null) {
                        marcarErrorDeEnvio(activoOriginal,
                                "Al activo le faltan datos de oficina, predio o entidad: no se pudo armar el envío al VSIAF.");
                        return ResponseEntity.ok(Map.of("ok", true, "vsiaf", "ERROR",
                                "msg", "Guardado en la base, pero NO se envió al VSIAF: al activo le faltan datos de "
                                     + "oficina, predio o entidad. El VSIAF quedó con la información anterior."));
                    }

                    String entidadCode = activoOriginal.getOficina().getPredio().getEntidad().getEntidadCodigo();
                    String unidadCode = activoOriginal.getOficina().getPredio().getUnidad();

                    // Si la modificación cambió el auxiliar, el nuevo puede no existir todavía
                    // en AUXILIAR.DBF: mandarlo primero evita un CODAUX huérfano en el VSIAF.
                    String fallaAux = sincronizarAuxiliarDelActivo(activoOriginal, usuarioNombre);

                    actualDbfWriterService.actualizarDesdeActivo(
                        codigoOriginal,
                        activoOriginal,
                        entidadCode,
                        unidadCode,
                        usuarioNombre
                    );

                    // Encolar no es haber escrito: en modo cola la orden la aplica el worker
                    // VFPOLEDB y recién ahí el DBF cambia. La marca deja el activo visible
                    // como EN_COLA hasta que ColaConfirmacionScheduler lea la respuesta.
                    marcarEnvioAlVsiaf(activoOriginal);
                    activoService.save(activoOriginal);

                    if (fallaAux != null) {
                        return ResponseEntity.ok(Map.of("ok", true, "vsiaf", "PARCIAL",
                                "msg", "Cambios enviados al VSIAF, pero el auxiliar NO se pudo enviar ("
                                     + fallaAux + "): en el VSIAF el activo va a verse sin auxiliar."));
                    }
                    return ResponseEntity.ok(Map.of("ok", true, "vsiaf", estadoEnvio(),
                            "msg", "Cambios guardados en la base. " + detalleEnvio()));

                } catch (Exception e) {
                    log.error("Error sync DBF al modificar: {}", e.getMessage());
                    marcarErrorDeEnvio(activoOriginal, "No se pudo dejar la orden para el VSIAF: " + e.getMessage());
                    return ResponseEntity.ok(Map.of("ok", true, "vsiaf", "ERROR",
                            "msg", "Guardado en la base, pero FALLÓ el envío al VSIAF: " + e.getMessage()
                                 + ". Los dos sistemas quedaron distintos: revisá la cola o usá Conciliación."));
                }
            }

            return ResponseEntity.ok(Map.of("ok", true, "vsiaf", "NO_APLICA",
                    "msg", "Modificación guardada. El activo sigue PENDIENTE, así que no se envió nada al VSIAF."));

        } catch (Exception e) {
            log.error("Error fatal modificando activo", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    /**
     * Cambio URGENTE del código de un activo. A diferencia de {@link #modificar_activo}
     * (donde el código va bloqueado), esta acción sí reemplaza el código, pero exige:
     *   1. Que el usuario tenga el permiso {@link #PERMISO_EDITAR_CODIGO} (lo otorga
     *      un ADMINISTRADOR / SUPER USUARIO desde la pantalla de permisos).
     *   2. Re-confirmar su propia contraseña (re-autenticación).
     *   3. Un motivo obligatorio.
     * Valida formato y unicidad del nuevo código, actualiza BD y —si el activo ya
     * estaba ACTIVO— el DBF/VSIAF (localizando el registro por el código original),
     * y deja constancia en {@code historial_activo}.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/modificar-codigo-urgente")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> modificarCodigoUrgente(
            HttpServletRequest request,
            @RequestParam("idActivo") Long idActivo,
            @RequestParam("nuevoCodigo") String nuevoCodigoRaw,
            @RequestParam("password") String password,
            @RequestParam("motivo") String motivo) {

        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Sesión expirada. Vuelva a iniciar sesión."));
        }

        // 1. Permiso explícito.
        if (!tienePermisoEditarCodigo(request)) {
            log.warn("[CODIGO-URGENTE] Usuario '{}' intentó cambiar código sin permiso.", usuario.getUsuario());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("ok", false, "msg", "No tiene autorización para modificar el código de un activo. "
                            + "Solicite a un administrador que lo habilite."));
        }

        // 2. Motivo obligatorio.
        if (motivo == null || motivo.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "El motivo del cambio es obligatorio."));
        }
        String motivoLimpio = motivo.trim();

        // 3. Re-autenticación con la contraseña del propio usuario.
        if (password == null || !passwordEncoder.matches(password, usuario.getPassword())) {
            log.warn("[CODIGO-URGENTE] Contraseña incorrecta para usuario '{}'.", usuario.getUsuario());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Contraseña incorrecta."));
        }

        try {
            Activo activo = activoService.findById(idActivo);
            if (activo == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Activo no encontrado."));
            }

            String codigoOriginal = activo.getCodigo();
            String nuevoCodigo = (nuevoCodigoRaw == null) ? "" : nuevoCodigoRaw.trim().toUpperCase();

            // 4. Validaciones del nuevo código.
            if (nuevoCodigo.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "El nuevo código es obligatorio."));
            }
            if (nuevoCodigo.equalsIgnoreCase(codigoOriginal)) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "El nuevo código es igual al actual."));
            }
            if (!nuevoCodigo.matches("^[^-]+-[^-]+-[0-9]+-[0-9]+$")) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "Formato de código inválido. Use el formato MUN-PREDIO-GRUPO-CORRELATIVO."));
            }
            if (activoService.findByCodigo(nuevoCodigo).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "El código " + nuevoCodigo + " ya está en uso por otro activo."));
            }

            boolean estabaActivo = "ACTIVO".equalsIgnoreCase(activo.getEstado());

            // 5. Aplicar el cambio en BD.
            activo.setCodigo(nuevoCodigo);
            activo.setUsuMod(usuario.getUsuario());
            activo.setUsuario(usuario.getUsuario());
            activo.setFecMod(LocalDate.now());
            activo.setFechaUlt(LocalDate.now());
            activo.setModificacion(new Date());
            activo.setModificacionIdUsuario(usuario.getIdUsuario());
            activo.setApiEstado(Short.valueOf("3"));
            activoService.save(activo);
            log.info("[CODIGO-URGENTE] Usuario '{}' cambió código '{}' → '{}'. Motivo: {}",
                    usuario.getUsuario(), codigoOriginal, nuevoCodigo, motivoLimpio);

            // 6. Auditoría en historial_activo.
            registrarHistorialCambioCodigo(activo, codigoOriginal, nuevoCodigo, motivoLimpio, usuario);

            // 7. Sincronizar DBF/VSIAF sólo si el activo ya estaba publicado (ACTIVO).
            if (estabaActivo) {
                try {
                    if (activo.getOficina() == null
                            || activo.getOficina().getPredio() == null
                            || activo.getOficina().getPredio().getEntidad() == null) {
                        return ResponseEntity.ok(Map.of("ok", true, "codigo", nuevoCodigo,
                                "msg", "Código actualizado en BD. No se sincronizó VSIAF por faltar datos de la oficina."));
                    }
                    String entidadCode = activo.getOficina().getPredio().getEntidad().getEntidadCodigo();
                    String unidadCode = activo.getOficina().getPredio().getUnidad();

                    actualDbfWriterService.actualizarDesdeActivo(
                            codigoOriginal, activo, entidadCode, unidadCode, usuario.getUsuario());

                    return ResponseEntity.ok(Map.of("ok", true, "codigo", nuevoCodigo,
                            "msg", "Código actualizado correctamente en BD y VSIAF. Cambio registrado en el historial."));
                } catch (Exception e) {
                    log.error("[CODIGO-URGENTE] Error sincronizando DBF: {}", e.getMessage());
                    return ResponseEntity.ok(Map.of("ok", true, "codigo", nuevoCodigo,
                            "msg", "Código actualizado en BD, pero falló la sincronización con VSIAF: " + e.getMessage()));
                }
            }

            return ResponseEntity.ok(Map.of("ok", true, "codigo", nuevoCodigo,
                    "msg", "Código actualizado (activo PENDIENTE, no requiere sincronizar VSIAF)."));

        } catch (Exception e) {
            log.error("[CODIGO-URGENTE] Error fatal cambiando código del activo {}", idActivo, e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    /** Registra el cambio de código en {@code historial_activo} sin romper el flujo principal. */
    private void registrarHistorialCambioCodigo(Activo activo, String codigoAnterior, String codigoNuevo,
                                                String motivo, Usuario usuario) {
        try {
            HistorialActivo h = new HistorialActivo();
            h.setActivo(activo);
            h.setCodigoActivo(codigoNuevo);
            h.setTipoEvento("CAMBIO_CODIGO");
            h.setFechaEvento(LocalDateTime.now());
            h.setIdUsuario(usuario.getIdUsuario());
            h.setNombreUsuario(usuario.getUsuario());
            h.setDescripcionEvento(String.format(
                    "Cambio URGENTE de código: '%s' → '%s'. Motivo: %s",
                    codigoAnterior, codigoNuevo, motivo));
            if (activo.getOficina() != null) {
                h.setOficinaAnterior(activo.getOficina());
                h.setOficinaNueva(activo.getOficina());
                h.setNombreOficinaAnterior(activo.getOficina().getNombre());
                h.setNombreOficinaNueva(activo.getOficina().getNombre());
            }
            if (activo.getResponsable() != null) {
                h.setResponsableAnterior(activo.getResponsable());
                h.setResponsableNuevo(activo.getResponsable());
            }
            historialActivoDao.save(h);
        } catch (Exception e) {
            log.warn("[CODIGO-URGENTE] No se pudo registrar el historial del cambio de código: {}", e.getMessage());
        }
    }

    public static class TransferenciaMasivaRequest {
        public Long         idOficina;
        public Long         idResponsable;
        public List<String> codigos;
        public String       tipo;          // "INTERNA" (default) | "EXTERNA"
        public String       observacion;
        public String       documentoReferencia;
        public String       institucionDestino; // solo para EXTERNA
    }

    private Auxiliar resolverAuxiliarDestino(
        Auxiliar auxOrigen,
        Predio predioDestino,
        String usuNombre) {
 
        if (auxOrigen == null) return null;
        if (predioDestino == null) {
            log.warn("[AUX] resolverAuxiliarDestino: predioDestino es null, sin cambio.");
            return auxOrigen;
        }
        if (auxOrigen.getGrupoContable() == null) {
            log.warn("[AUX] El auxiliar '{}' no tiene grupoContable asignado.", auxOrigen.getNombre());
            return null;
        }
    
        Long idPredioDest = predioDestino.getIdPredio();
        Long idGrupo      = auxOrigen.getGrupoContable().getIdGrupoContable();
        // NOMAUX en auxiliar.DBF son 60 caracteres: si el nombre se pasa, el VSIAF lo corta
        // por su cuenta y deja de coincidir con el de la BD (así se emparejan los auxiliares
        // entre predios). Se recorta acá para que los dos lados guarden lo mismo.
        String nombreAux  = auxOrigen.getNombre().trim().toUpperCase();
        if (nombreAux.length() > 60) nombreAux = nombreAux.substring(0, 60);

        log.info("[AUX] Resolviendo auxiliar '{}' | GrupoID={} | PredioDestID={}",
            nombreAux, idGrupo, idPredioDest);
    
        // 1. Buscar coincidencia exacta por nombre en el predio destino
        Optional<Auxiliar> auxExistente = auxiliarService
            .findByPredioIdPredioAndGrupoContableIdGrupoContableAndNombreIgnoreCase(
                idPredioDest, idGrupo, nombreAux);
    
        if (auxExistente.isPresent()) {
            Auxiliar encontrado = auxExistente.get();
            log.info("[AUX] Auxiliar ya existe en destino: '{}' CodAux={} (ID={})",
                nombreAux, encontrado.getCodAux(), encontrado.getIdAuxiliar());
            return encontrado;
        }
    
        // 2. No existe → crear en BD con el siguiente codAux correlativo
        // ✅ Fix de tipo: Integer, no Short
        Integer maxCodAux = auxiliarService.findMaxCodAux(idPredioDest, idGrupo);
        if (maxCodAux == null) maxCodAux = 0; // seguridad extra
        short nextCod = (short) (maxCodAux + 1);
    
        log.info("[AUX] Creando nuevo auxiliar '{}' para predio '{}': CodAux={}",
            nombreAux, predioDestino.getDescrip(), nextCod);
    
        Auxiliar nuevoAux = new Auxiliar();
        nuevoAux.setNombre(nombreAux);
        nuevoAux.setGrupoContable(auxOrigen.getGrupoContable());
        nuevoAux.setPredio(predioDestino);
        nuevoAux.setObserv(auxOrigen.getObserv());
        nuevoAux.setUsuario(usuNombre);
        nuevoAux.setFechaUlt(LocalDate.now());
        nuevoAux.setCodAux(nextCod);
        // Sin estado, el auxiliar queda invisible para las consultas que filtran por ACTIVO
        // (entre ellas la que busca el auxiliar origen de una transferencia).
        nuevoAux.setEstado("ACTIVO");

        nuevoAux = auxiliarService.save(nuevoAux);
        log.info("[AUX] Nuevo auxiliar guardado en BD: ID={} '{}' CodAux={}",
            nuevoAux.getIdAuxiliar(), nombreAux, nextCod);

        // 3. Sincronizar con auxiliar.DBF por la misma vía que el resto (cola → worker VFPOLEDB)
        try {
            auxiliarDbfWriterService.asegurarEnVsiaf(nuevoAux, usuNombre);
            log.info("[AUX-DBF] Auxiliar '{}' enviado al VSIAF (unidad='{}')",
                nombreAux, predioDestino.getUnidad());
        } catch (Exception e) {
            // No revertir: el auxiliar ya está en BD, el DBF se puede re-sincronizar después
            log.error("[AUX-DBF] Auxiliar creado en BD pero NO enviado al VSIAF: {}", e.getMessage());
        }

        return nuevoAux;
    }

    @PostMapping("/transferencia-masiva")
    @ResponseBody
    public ResponseEntity<?> transferenciaMasiva(
            HttpServletRequest request,
            @RequestBody TransferenciaMasivaRequest payload) {
    
        Usuario usuario    = (Usuario) request.getSession().getAttribute("usuario");
        String  usuNombre  = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
        Long    usuId      = (usuario != null) ? usuario.getIdUsuario() : null;
        String  tipo       = (payload.tipo != null) ? payload.tipo.toUpperCase() : "INTERNA";
        
    
        try {
            Oficina     ofDestino   = oficinaService.findById(payload.idOficina);
            Responsable respDestino = responsableService.findById(payload.idResponsable);
    
            if (ofDestino == null || respDestino == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "Oficina o Responsable no válidos."));
            }

            String entidadCode = "";
            String unidadCode  = "";
            if (ofDestino.getPredio() != null) {
                unidadCode = ofDestino.getPredio().getUnidad() != null
                            ? ofDestino.getPredio().getUnidad() : "";
                if (ofDestino.getPredio().getEntidad() != null) {
                    entidadCode = ofDestino.getPredio().getEntidad().getEntidadCodigo() != null
                                ? ofDestino.getPredio().getEntidad().getEntidadCodigo() : "";
                }
            }
            log.info("[TRANSF] Predio destino → entidad='{}' unidad='{}'", entidadCode, unidadCode);
    
            // 1. Buscar activos y capturar estado ANTES del cambio
            List<TransferenciaService.ActivoConOrigen> acos = new ArrayList<>();
            for (String codigo : payload.codigos) {
                activoService.findByCodigo(codigo).ifPresent(a ->
                    acos.add(new TransferenciaService.ActivoConOrigen(a))
                );
            }
    
            if (acos.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "No se encontraron los activos proporcionados."));
            }

            Map<Long, Auxiliar> cacheAuxiliaresDestino = new HashMap<>();
    
            // 2. Modificar los activos en memoria (DESPUÉS de capturar el origen)
            LocalDate hoy = LocalDate.now();
            for (TransferenciaService.ActivoConOrigen ac : acos) {
                Activo a = ac.activo;
                if ("EXTERNA".equalsIgnoreCase(tipo)) {
 
                    // Paso A: Resolver el auxiliar del predio destino PRIMERO
                    if (a.getAuxiliar() != null) {
                        Long idAuxOriginal = a.getAuxiliar().getIdAuxiliar();
                        Auxiliar auxDestino = cacheAuxiliaresDestino.computeIfAbsent(
                            idAuxOriginal,
                            id -> resolverAuxiliarDestino(
                                a.getAuxiliar(),
                                ofDestino.getPredio(),
                                usuNombre
                            )
                        );
                        // Asigna el auxiliar correcto ANTES de modificar otros campos
                        a.setAuxiliar(auxDestino);
            
                        log.info("[TRANSF-EXT] Activo '{}': CODAUX {} → {}",
                            a.getCodigo(),
                            ac.activo.getAuxiliar() != null ? ac.activo.getAuxiliar().getCodAux() : "null",
                            auxDestino != null ? auxDestino.getCodAux() : "null");
                    }
                }
                a.setOficina(ofDestino);
                a.setResponsable(respDestino);
                a.setFecMod(hoy);
                a.setUsuMod(usuNombre);
                a.setFechaUlt(hoy);
                a.setUsuario(usuNombre);
                a.setApiEstado(Short.valueOf("3"));
                if (usuario != null) a.setModificacionIdUsuario(usuId);
                a.setModificacion(new java.util.Date());
            }
    
            // 3. Guardar en BD
            for (TransferenciaService.ActivoConOrigen ac : acos) {
                activoService.save(ac.activo);
            }
            log.info("Transferidos {} activos → Oficina ID: {}", acos.size(), payload.idOficina);
    
            // 4. Registrar transferencia + historial automáticamente
            Transferencia trf = transferenciaService.registrarTransferencia(
                acos, tipo, ofDestino, respDestino, usuId, usuNombre
            );
            // Campos adicionales opcionales
            String numeroTrf = "S/N";
            if (trf != null) {
                if (payload.observacion != null)        trf.setObservacion(payload.observacion);
                if (payload.documentoReferencia != null) trf.setDocumentoReferencia(payload.documentoReferencia);
                if (payload.institucionDestino != null)  trf.setInstitucionDestino(payload.institucionDestino);
                transferenciaDao.save(trf); // update con los campos extra
                
                if (trf.getNumeroTransferencia() != null) {
                    numeroTrf = trf.getNumeroTransferencia();
                }
            }

            log.info("[DBF-DIAG] tipo={} | entidad='{}' | unidad='{}' | activos={}",
                tipo, "entidadCode", "unidadCode", acos.size());
            acos.forEach(ac -> log.info("[DBF-DIAG] código='{}' oficinaNueva='{}'",
                ac.activo.getCodigo(),
                ac.activo.getOficina() != null ? ac.activo.getOficina().getNombre() : "NULL"));

            // ── Sincronizar Responsable destino si es nuevo ───────────────────────
            try {
                Short codOfic = ofDestino.getCodOfi();
                if (codOfic != null && respDestino.getCodigoFuncionario() != null) {
                    String onlyDigits = respDestino.getCodigoFuncionario().replaceAll("\\D+", "");
                    if (!onlyDigits.isEmpty()) {
                        Integer codResp = Integer.valueOf(onlyDigits);
                        log.info("[TRANSF] Verificando responsable codResp={} entidad='{}' unidad='{}' en DBF",
                                codResp, entidadCode, unidadCode);

                        boolean existeEnDbf = respDbfWriterService.existsByCodResp(
                                codResp, codOfic, entidadCode, unidadCode);

                        if (!existeEnDbf) {
                            log.info("[TRANSF] Responsable no encontrado en DBF — insertando...");
                            respDbfWriterService.insertarDesdeResponsable(
                                    respDestino, entidadCode, unidadCode, usuNombre);
                            log.info("[TRANSF] Responsable codResp={} insertado en DBF correctamente", codResp);
                        } else {
                            log.info("[TRANSF] Responsable codResp={} ya existe en DBF — omitido", codResp);
                        }

                        // Marcar como sincronizado si era nuevo (apiEstado 1 o null)
                        Short estadoActual = respDestino.getApiEstado();
                        if (estadoActual == null || estadoActual == 1) {
                            respDestino.setApiEstado(Short.valueOf("0"));
                            responsableService.save(respDestino);
                        }
                    }
                }
            } catch (Exception e) {
                // No es fatal — los activos ya se guardaron en BD, solo falló el DBF del responsable
                log.warn("[TRANSF] No se pudo sincronizar responsable a DBF: {}", e.getMessage());
            }
    
            // 5. Sincronizar DBF
            try {
                
                List<Activo> activos = acos.stream().map(ac -> ac.activo).toList();

                log.info("[DBF-DIAG] Tipo transferencia: {}", tipo);
                log.info("[DBF-DIAG] entidadCode = '{}' (vacío={})", entidadCode, entidadCode.isBlank());
                log.info("[DBF-DIAG] unidadCode  = '{}' (vacío={})", unidadCode, unidadCode.isBlank());
                log.info("[DBF-DIAG] Activos a sincronizar: {}", acos.size());
                acos.forEach(ac -> log.info("[DBF-DIAG]   código='{}' | oficina='{}'",
                    ac.activo.getCodigo(),
                    ac.activo.getOficina() != null ? ac.activo.getOficina().getNombre() : "NULL"));

                actualDbfWriterService.actualizarLoteTransferencias(activos, entidadCode, unidadCode, usuNombre);
    
                return ResponseEntity.ok(Map.of(
                    "ok",  true,
                    "msg", String.format("Se transfirieron %d activos (BD + DBF). Ref: %s",
                                        acos.size(), numeroTrf),
                    "numeroTransferencia", numeroTrf
                ));
            } catch (Exception e) {
                log.error("Error sincronizando lote DBF: {}", e.getMessage());
                return ResponseEntity.ok(Map.of(
                    "ok",  true,
                    "msg", String.format("Guardado en BD (%d activos). DBF falló: %s. Ref: %s",
                                        acos.size(), e.getMessage() != null ? e.getMessage() : "Desconocido", numeroTrf),
                    "numeroTransferencia", numeroTrf
                ));
            }
    
        } catch (Exception e) {
            log.error("Error fatal en transferencia masiva", e);
            return ResponseEntity.status(500)
                .body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    @GetMapping("/transferencias")
    @ResponseBody
    public ResponseEntity<?> listarTransferencias(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        try {
            List<Transferencia> lista = transferenciaDao.buscarFiltrado(tipo, desde, hasta);
            List<Map<String, Object>> result = lista.stream().map(t -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id",              t.getIdTransferencia());
                m.put("numero",          t.getNumeroTransferencia());
                m.put("tipo",            t.getTipo());
                m.put("fecha",           t.getFechaTransferencia().toString());
                m.put("estadoProceso",   t.getEstadoProceso());
                m.put("ofDestino",       t.getOficinaDestino() != null ? t.getOficinaDestino().getNombre() : null);
                m.put("ofOrigen",        t.getOficinaOrigen()  != null ? t.getOficinaOrigen().getNombre()  : null);
                m.put("respDestino",     t.getResponsableDestino() != null ? t.getResponsableDestino().getPersona().getNombreCompleto() : null);
                m.put("cantidadActivos", t.getDetalles().size());
                m.put("usuario",         t.getRegistro() != null ? t.getRegistro().toString() : null);
                return m;
            }).toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    public class AsignacionMasivaRequest {
        public Long    idOficina;
        public Long    idResponsableOrigen;
        public Long    idResponsableDestino;
        public List<String> codigos;
    }

    @PostMapping("/asignacion-masiva")
    @ResponseBody
    public ResponseEntity<?> asignacionMasiva(
            HttpServletRequest request,
            @RequestBody AsignacionMasivaRequest payload) {
    
        Usuario usuario   = (Usuario) request.getSession().getAttribute("usuario");
        String  usuNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
        Long    usuId     = (usuario != null) ? usuario.getIdUsuario() : null;
    
        try {
            // 1. Validar destino
            Responsable respDestino = responsableService.findById(payload.idResponsableDestino);
            Oficina     oficina     = oficinaService.findById(payload.idOficina);
    
            if (respDestino == null || oficina == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "Responsable destino u oficina no válidos."));
            }
    
            // Seguridad: el resp destino debe pertenecer a la misma oficina
            if (respDestino.getOficina() == null ||
                !respDestino.getOficina().getIdOficina().equals(payload.idOficina)) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "El responsable destino no pertenece a la oficina indicada."));
            }
    
            // 2. Buscar activos del responsable origen
            List<Activo> activos = new ArrayList<>();
            for (String codigo : payload.codigos) {
                activoService.findByCodigo(codigo).ifPresent(activos::add);
            }
    
            if (activos.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "No se encontraron los activos proporcionados."));
            }
    
            // Verificación extra: todos los activos deben pertenecer al resp origen
            // (evitar manipulación de payload)
            activos.removeIf(a ->
                a.getResponsable() == null ||
                !a.getResponsable().getIdResponsable().equals(payload.idResponsableOrigen)
            );
    
            if (activos.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg",
                        "Ninguno de los activos pertenece al responsable origen indicado."));
            }
    
            // 3. Modificar en memoria — SOLO el responsable cambia (oficina permanece igual)
            LocalDate hoy = LocalDate.now();
            for (Activo a : activos) {
                a.setResponsable(respDestino);
                a.setFecMod(hoy);
                a.setUsuMod(usuNombre);
                a.setFechaUlt(hoy);
                a.setUsuario(usuNombre);
                a.setApiEstado(Short.valueOf("3"));   // modificado
                if (usuario != null) a.setModificacionIdUsuario(usuId);
                a.setModificacion(new java.util.Date());
            }
    
            // 4. Guardar en BD
            for (Activo a : activos) {
                activoService.save(a);
            }
            log.info("[ASIGNACION] {} activos reasignados → Resp ID: {} | Usuario: {}",
                activos.size(), payload.idResponsableDestino, usuNombre);

            // ── Sincronizar Responsable destino si es nuevo (apiEstado == 1) ──────────────
            try {
                String entCode = "";
                String uniCode = "";
                if (oficina.getPredio() != null) {
                    uniCode = oficina.getPredio().getUnidad() != null
                            ? oficina.getPredio().getUnidad() : "";
                    if (oficina.getPredio().getEntidad() != null)
                        entCode = oficina.getPredio().getEntidad().getEntidadCodigo() != null
                                ? oficina.getPredio().getEntidad().getEntidadCodigo() : "";
                }
                Short codOfic = oficina.getCodOfi();
                if (codOfic != null && respDestino.getCodigoFuncionario() != null) {
                    String onlyDigits = respDestino.getCodigoFuncionario().replaceAll("\\D+", "");
                    if (!onlyDigits.isEmpty()) {
                        Integer codResp = Integer.valueOf(onlyDigits);
                        log.info("[ASIGNACION] Verificando responsable codResp={} entidad='{}' unidad='{}' en DBF",
                                codResp, entCode, uniCode);

                        boolean existeEnDbf = respDbfWriterService.existsByCodResp(
                                codResp, codOfic, entCode, uniCode);

                        if (!existeEnDbf) {
                            log.info("[ASIGNACION] Responsable no encontrado en DBF — insertando...");
                            respDbfWriterService.insertarDesdeResponsable(
                                    respDestino, entCode, uniCode, usuNombre);
                            log.info("[ASIGNACION] Responsable codResp={} insertado en DBF correctamente", codResp);
                        } else {
                            log.info("[ASIGNACION] Responsable codResp={} ya existe en DBF — omitido", codResp);
                        }

                        Short estadoActual = respDestino.getApiEstado();
                        if (estadoActual == null || estadoActual == 1) {
                            respDestino.setApiEstado(Short.valueOf("0"));
                            responsableService.save(respDestino);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[ASIGNACION] No se pudo sincronizar responsable a DBF: {}", e.getMessage());
            }
    
            // 5. Sincronizar DBF (solo actualiza CODRESP y campos de auditoría)
            String entidadCode = "";
            String unidadCode  = "";
            if (oficina.getPredio() != null) {
                unidadCode = oficina.getPredio().getUnidad() != null
                            ? oficina.getPredio().getUnidad() : "";
                if (oficina.getPredio().getEntidad() != null) {
                    entidadCode = oficina.getPredio().getEntidad().getEntidadCodigo() != null
                                ? oficina.getPredio().getEntidad().getEntidadCodigo() : "";
                }
            }
    
            try {
                actualDbfWriterService.actualizarLoteTransferencias(
                    activos, entidadCode, unidadCode, usuNombre
                );
                return ResponseEntity.ok(Map.of(
                    "ok",  true,
                    "msg", String.format("%d activo(s) reasignados a %s (BD + DBF).",
                        activos.size(), respDestino.getPersona() != null
                            ? respDestino.getPersona().getNombre() : respDestino.getCodigoFuncionario())
                ));
            } catch (Exception e) {
                log.error("[ASIGNACION] Error sincronizando DBF: {}", e.getMessage());
                return ResponseEntity.ok(Map.of(
                    "ok",  true,
                    "msg", String.format("%d activo(s) reasignados en BD. DBF falló: %s",
                        activos.size(), e.getMessage() != null ? e.getMessage() : "Error desconocido")
                ));
            }
    
        } catch (Exception e) {
            log.error("[ASIGNACION] Error fatal", e);
            return ResponseEntity.status(500)
                .body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/baja-activo")
    @ResponseBody
    public ResponseEntity<?> baja_activo(@RequestParam("idActivo") Long idActivo, HttpServletRequest request) {
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

        try {
            Activo activo = activoService.findById(idActivo);
            if (activo == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Activo no encontrado"));
            }
            
            if (!"ACTIVO".equalsIgnoreCase(activo.getEstado())) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Solo se puede dar de baja activos que estén en estado ACTIVO."));
            }

            activo.setApiEstado(Short.valueOf("1")); 
            activo.setFecMod(LocalDate.now());
            activo.setFechaUlt(LocalDate.now());
            activo.setUsuMod(usuarioNombre);
            activo.setModificacion(new Date());

            if (usuario != null) activo.setModificacionIdUsuario(usuario.getIdUsuario());

            activoService.save(activo);
            
            try {

                String codigo = activo.getCodigo();
                String entidadCode = activo.getOficina().getPredio().getEntidad().getEntidadCodigo();
                String unidadCode = activo.getOficina().getPredio().getUnidad();
                
                actualDbfWriterService.actualizarDesdeActivo(codigo, activo, entidadCode, unidadCode, usuarioNombre);
                
                log.info("Activo {} dado de baja (API_ESTADO=2) en BD y DBF", codigo);
                return ResponseEntity.ok(Map.of("ok", true, "msg", "Activo dado de baja correctamente en ambos sistemas."));

            } catch (Exception e) {
                log.error("Error sync DBF al dar de baja: {}", e.getMessage());
                return ResponseEntity.ok(Map.of("ok", true, "msg", "Estado actualizado en BD, pero falló DBF: " + e.getMessage()));
            }

        } catch (Exception e) {
            log.error("Error en baja activo", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/asignar-gestion-masiva")
    @ResponseBody
    public ResponseEntity<?> asignarGestionMasiva(
            @RequestParam("ids")      List<String> idsEnc,
            @RequestParam("idConfig") Long idConfig,
            @RequestParam("nroDoc")   String nroDoc) {

        try {
            ConfiguracionGestion config = configuracionGestionService.findById(idConfig);
            String prefijo        = config.getPrefijoDocumento() != null ? config.getPrefijoDocumento().trim() : "";
            String nro            = nroDoc != null ? nroDoc.trim() : "";
            // El código se guarda sin paréntesis —esa es la forma canónica, la que se
            // busca y se compara— y los paréntesis se agregan solo donde son parte de la
            // presentación: el prefijo de la descripción del activo y el encabezado del
            // acta. Antes se guardaba con paréntesis por acá y sin ellos desde Reportes.
            String codigoCompleto = (prefijo + " " + nro).trim();
            String etiquetaDoc    = "(" + codigoCompleto + ")";

            List<Activo> activos = new ArrayList<>();
            for (String enc : idsEnc) {
                Long id = Long.parseLong(Encriptar.decrypt(enc));
                Activo a = activoService.findById(id);
                if (a != null && "PENDIENTE".equalsIgnoreCase(a.getEstado())) {
                    activos.add(a);
                }
            }
            if (activos.isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "No hay activos pendientes."));
            }

            // No se puede asignar documento si algún activo tiene datos obligatorios incompletos
            List<String> incompletos = new ArrayList<>();
            for (Activo a : activos) {
                List<String> falt = camposFaltantesVsiaf(a);
                if (!falt.isEmpty()) {
                    incompletos.add(a.getCodigo() + " (falta: " + String.join(", ", falt) + ")");
                }
            }
            if (!incompletos.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "ok", false,
                    "msg", "No se puede asignar el documento: hay activos con datos incompletos.",
                    "incompletos", incompletos));
            }

            for (Activo a : activos) {
                if (!a.getDescripcion().startsWith(etiquetaDoc)) {
                    String nueva = etiquetaDoc + " " + a.getDescripcion();
                    if (nueva.length() > 1024) nueva = nueva.substring(0, 1024);
                    a.setDescripcion(nueva);
                    a.setFecMod(LocalDate.now());
                    activoService.save(a);
                }
            }

            AsignacionActivo asignacion = asignacionActivoService
                    .findByActivo(activos.get(0))
                    .orElse(null);

            if (asignacion == null) {
                Activo referencia = activos.get(0);
                asignacion = new AsignacionActivo();
                asignacion.setFechaAsignacion(LocalDateTime.now());
                asignacion.setResponsable(referencia.getResponsable());
                // La cabecera se llenaba a medias: sin oficina destino y con tipo y estado
                // en su valor por defecto. Por eso los filtros de TIPO y ESTADO no
                // encontraban nada. El número del acta NO se genera acá: es el preventivo,
                // y se carga más abajo con asignarDocumento().
                asignacion.setOficinaDestino(referencia.getOficina());
                asignacion.setTipoAsignacion("NUEVA");
                asignacion.setEstadoAsignacion("ACTIVA");
                asignacion = asignacionActivoService.save(asignacion);

                for (Activo a : activos) {
                    DetalleAsignacionActivo det = new DetalleAsignacionActivo();
                    det.setAsignacionActivo(asignacion);
                    det.setActivo(a);
                    det.setCodigoActivoSnapshot(a.getCodigo());
                    det.setEstadoDetalle(DetalleAsignacionActivo.VIGENTE);
                    detalleAsignacionActivoService.save(det);
                }
            }

            // Deja documento, código canónico y número de acta coherentes de una sola vez.
            // Se aplica también cuando el acta ya existía: si se corrige el número de
            // documento, el número del acta tiene que corregirse con él.
            asignacion.asignarDocumento(config.getGestion(), prefijo, nro);
            asignacionActivoService.save(asignacion);

            String idEnc = Encriptar.encrypt(String.valueOf(asignacion.getIdAsignacionActivo()));

            notificarCambioPendientes("asignacion-doc");
            return ResponseEntity.ok(Map.of(
                "ok",             true,
                "msg",            "Documento asignado a " + activos.size() + " activo(s).",
                "idAsignacionActivo",   idEnc,
                "codigoCompleto", codigoCompleto
            ));

        } catch (Exception e) {
            log.error("Error en asignarGestionMasiva", e);
            return ResponseEntity.badRequest()
                .body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/datos-asignacion")
    @ResponseBody
    public ResponseEntity<?> datosAsignacion(@RequestParam String idAsigEnc) throws Exception {
        Long idAsig = Long.parseLong(Encriptar.decrypt(idAsigEnc));
        AsignacionActivo asig = asignacionActivoService.findById(idAsig);
        if (asig == null)
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Asignación no encontrada"));

        Activo ref = null;
        if (asig.getDetalles() != null) {
            for (DetalleAsignacionActivo d : asig.getDetalles()) {
                if (d.getActivo() != null) { ref = d.getActivo(); break; }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("codigoAsignacion", asig.getCodigoCompleto());
        if (ref != null) {
            String[] parts = ref.getCodigo().split("-");
            data.put("codMun", parts.length > 0 ? parts[0] : "");
            data.put("codPred", parts.length > 1 ? parts[1] : "");
            data.put("codGrp",  parts.length > 2 ? parts[2] : "");
            data.put("municipio", ref.getOficina().getPredio().getMunicipio().getNombre());
            data.put("predio", ref.getOficina().getPredio().getDescrip());
            data.put("oficina", ref.getOficina().getNombre());
            data.put("responsable", ref.getResponsable().getPersona().getNombreCompleto());
            data.put("grupo", ref.getGrupoContable().getNombre());
            data.put("idPredio", ref.getOficina().getPredio().getIdPredio());
        }
        return ResponseEntity.ok(data);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/api/agregar-activo-pendiente")
    @ResponseBody
    public ResponseEntity<?> agregarActivoPendiente(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) throws Exception {

        String idAsigEnc = (String) body.get("idAsigEnc");
        Long idAsig = Long.parseLong(Encriptar.decrypt(idAsigEnc));
        AsignacionActivo asig = asignacionActivoService.findById(idAsig);
        if (asig == null)
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Asignación no encontrada"));

        String descripcion     = (String) body.get("descripcion");
        Double costo           = toDouble(body.get("costo"), 0.0);
        BigDecimal vidaUtil    = BigDecimal.valueOf(toDouble(body.get("vidaUtil"), 0.0));
        LocalDate fechaAdq     = body.get("fechaAdquisicion") instanceof String ? LocalDate.parse((String) body.get("fechaAdquisicion")) : null;
        Long idOrgFin          = toLong(body.get("idOrganismoFinanciero"));
        Integer cantidad       = toInteger(body.get("cantidad"), 1);
        Boolean incluyeAcc     = body.get("incluyeAccesorio") instanceof Boolean && (Boolean) body.get("incluyeAccesorio");
        Long idGrupoContable   = toLong(body.get("idGrupoContable"));
        Long idAuxiliar        = toLong(body.get("idAuxiliar"));
        String codigoLibreRaw  = (String) body.get("codigo");
        List<Map<String, Object>> detallesRaw = (List<Map<String, Object>>) body.get("detalles");

        String codigoRef = null;
        Responsable responsable = asig.getResponsable();
        Oficina oficina = asig.getOficinaDestino();

        if (asig.getDetalles() != null) {
            for (DetalleAsignacionActivo d : asig.getDetalles()) {
                if (d.getActivo() != null) {
                    if (codigoRef == null) codigoRef = d.getActivo().getCodigo();
                    if (responsable == null) responsable = d.getActivo().getResponsable();
                    if (oficina == null)     oficina     = d.getActivo().getOficina();
                    if (codigoRef != null) break;
                }
            }
        }
        if (codigoRef == null)
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No hay activo de referencia en esta asignación"));

        GrupoContable grupo = idGrupoContable != null ? grupoContableService.findById(idGrupoContable) : null;
        if (grupo == null) {
            if (asig.getDetalles() != null) {
                for (DetalleAsignacionActivo d : asig.getDetalles()) {
                    if (d.getActivo() != null && d.getActivo().getGrupoContable() != null) {
                        grupo = d.getActivo().getGrupoContable();
                        break;
                    }
                }
            }
        }
        if (grupo == null)
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No se pudo determinar el grupo contable"));

        // El auxiliar de la asignación tiene que ser del predio de la oficina destino y del
        // grupo contable elegido: si no, el CODAUX que se mande al VSIAF no corresponde y el
        // activo aparece allá sin auxiliar.
        Auxiliar auxiliar;
        try {
            auxiliar = resolverAuxiliarCoherente(idAuxiliar, oficina, grupo);
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        }

        String[] parts = codigoRef.split("-");
        String codMun = parts.length > 0 ? parts[0] : "";
        String codPred = parts.length > 1 ? parts[1] : "";
        String codGrp = String.format("%02d", grupo.getCodDbf());

        // Código libre (hueco) elegido a mano en vez del correlativo automático: solo
        // tiene sentido para UN activo a la vez (no hay forma de que el usuario elija N
        // huecos puntuales desde este modal).
        String codigoManual = (codigoLibreRaw != null && !codigoLibreRaw.isBlank())
                ? codigoLibreRaw.trim().toUpperCase() : null;
        if (codigoManual != null) {
            if (cantidad != 1) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "Un código libre solo se puede asignar a un activo a la vez (cantidad = 1)."));
            }
            if (!codigoManual.matches("^[^-]+-[^-]+-[0-9]+-[0-9]+$")) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Código con formato inválido."));
            }
            String prefijoEsperado = codMun + "-" + codPred + "-" + codGrp;
            String prefijoCodigo = codigoManual.substring(0, codigoManual.lastIndexOf('-'));
            if (!prefijoEsperado.equals(prefijoCodigo)) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "El código no corresponde al predio/grupo de esta asignación (esperado " + prefijoEsperado + ")."));
            }
            if (activoService.findByCodigo(codigoManual).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "El código " + codigoManual + " ya fue tomado. Elige otro."));
            }
        }

        OrganismoFinanciero orgFin = idOrgFin != null ? organismoFinancieroService.findById(idOrgFin) : null;
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

        List<DetalleActivoDTO> detalles = new ArrayList<>();
        if (detallesRaw != null) {
            for (Map<String, Object> dr : detallesRaw) {
                DetalleActivoDTO d = new DetalleActivoDTO();
                d.setDescripcion((String) dr.get("descripcion"));
                d.setSerie((String) dr.get("serie"));
                d.setMarca((String) dr.get("marca"));
                d.setModelo((String) dr.get("modelo"));
                d.setColor((String) dr.get("color"));
                detalles.add(d);
            }
        }
        while (detalles.size() < cantidad) {
            DetalleActivoDTO d = new DetalleActivoDTO();
            d.setDescripcion(descripcion);
            detalles.add(d);
        }

        List<String> codesCreados = new ArrayList<>();
        int total = Math.min(cantidad, detalles.size());

        for (int i = 0; i < total; i++) {
            DetalleActivoDTO det = detalles.get(i);
            String nuevoCodigo = (codigoManual != null)
                    ? codigoManual
                    : funciones.generarCodigoPorCodes(codMun, codPred, codGrp);

            Activo a = new Activo();
            a.setCodigo(nuevoCodigo);
            String descBase = (det.getDescripcion() != null && !det.getDescripcion().isBlank()) ? det.getDescripcion() : descripcion;
            a.setDescripcion(construirDescripcionActivo(descBase, det.getColor(), det.getMarca(), det.getModelo(), det.getSerie(), incluyeAcc));
            a.setFechaAdquisicion(fechaAdq);
            a.setVidaUtil(vidaUtil);
            a.setCosto(costo);
            a.setResponsable(responsable);
            a.setOrganismoFinanciero(orgFin);
            if (orgFin != null) a.setOrgFinCode(orgFin.getCodOf());
            a.setGrupoContable(grupo);
            a.setAuxiliar(auxiliar);
            a.setOficina(oficina);
            a.setEstado("PENDIENTE");
            a.setApiEstado((short) 3);
            a.setVidaUtilAnterior(0);
            a.setEstadoActivo(estadoActivoService.findById(1L));
            a.setCostoAnterior(0.0);
            a.setDepreciacionAcum(0.0);
            a.setUsuario(usuarioNombre);
            a.setFecMod(LocalDate.now());
            a.setFechaUlt(LocalDate.now());
            try {
                activoService.save(a);
            } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "El código " + nuevoCodigo + " acaba de ser tomado por otro registro. Intenta de nuevo."));
            }

            DetalleAsignacionActivo detalleAsig = new DetalleAsignacionActivo();
            detalleAsig.setAsignacionActivo(asig);
            detalleAsig.setActivo(a);
            detalleAsig.setEstadoDetalle(DetalleAsignacionActivo.VIGENTE);
            detalleAsig.setCodigoActivoSnapshot(a.getCodigo());
            detalleAsig.setDescripcionActivoSnapshot(a.getDescripcion());
            detalleAsig.setCostoActivoSnapshot(BigDecimal.valueOf(a.getCosto()));
            detalleAsig.setEstadoActivoSnapshot(a.getEstadoActivo().getNombre());
            detalleAsignacionActivoService.save(detalleAsig);

            codesCreados.add(nuevoCodigo);
        }

        notificarCambioPendientes("registro");
        return ResponseEntity.ok(Map.of(
            "ok", true,
            "msg", total + " activo(s) creado(s): " + String.join(", ", codesCreados),
            "codes", codesCreados
        ));
    }

    @ValidarUsuarioAutenticado
    @PostMapping(value = "/api/editar-pendiente/{idEnc}",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> editarPendiente(
            @PathVariable String idEnc,
            @RequestBody EditarActivoPendienteRequest req,
            HttpServletRequest httpReq) {

        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            Activo a = activoService.findById(id);

            if (a == null)
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Activo no encontrado."));
            if (!"PENDIENTE".equalsIgnoreCase(a.getEstado()))
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Solo se pueden editar activos PENDIENTE."));

            aplicarCambios(a, req);
            a.setFecMod(LocalDate.now());
            a.setUsuMod(obtenerUsuario(httpReq));
            activoService.save(a);

            return ResponseEntity.ok(Map.of("ok", true, "msg", "Activo actualizado."));
        } catch (IllegalArgumentException datoInvalido) {
            // Típicamente: auxiliar que no es del predio/grupo del activo. Es un error del
            // usuario, no del servidor: se responde 400 con el motivo exacto.
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("Error editando activo pendiente", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    /** Roles autorizados a corregir un activo ya registrado (historial de asignaciones). */
    private static final Set<String> ROLES_EDICION_REGISTRADA = Set.of("ADMINISTRADOR", "SUPER USUARIO");

    /**
     * ¿El usuario en sesión puede corregir activos ya registrados?
     * <p>
     * La comprobación va acá y no en {@code SeguridadConfig}: en este proyecto casi
     * todas las rutas están en {@code permitAll()}, así que un matcher por URL no
     * protegería nada. Ocultar el botón en la vista tampoco: cualquiera puede llamar
     * al endpoint a mano.
     */
    private boolean puedeEditarRegistrado(Usuario usuario) {
        if (usuario == null || usuario.getRol() == null || usuario.getRol().getNombre() == null) return false;
        return ROLES_EDICION_REGISTRADA.contains(usuario.getRol().getNombre().trim().toUpperCase());
    }

    /**
     * Corrección de un activo <b>ya registrado</b>, desde el historial de asignaciones.
     * <p>
     * Se diferencia de {@link #editarPendiente} en tres cosas: exige rol
     * ADMINISTRADOR / SUPER USUARIO, propaga el cambio al VSIAF cuando el activo ya
     * está publicado, y deja constancia en {@code historial_activo}.
     * <p>
     * El <b>código no se toca</b>: es la llave con la que se ubica el registro en el
     * DBF. Para cambiarlo existe {@link #modificarCodigoUrgente}, que pide permiso
     * explícito, contraseña y motivo.
     */
    @ValidarUsuarioAutenticado
    @PostMapping(value = "/api/editar-registrado/{idEnc}",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional
    public ResponseEntity<?> editarRegistrado(
            @PathVariable String idEnc,
            @RequestBody EditarActivoPendienteRequest req,
            HttpServletRequest httpReq) {

        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "msg", "Sesión expirada. Vuelva a iniciar sesión."));
        }
        if (!puedeEditarRegistrado(usuario)) {
            log.warn("[EDITAR-REGISTRADO] '{}' (rol {}) intentó corregir un activo registrado.",
                    usuario.getUsuario(),
                    usuario.getRol() != null ? usuario.getRol().getNombre() : "?");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false,
                    "msg", "Solo un ADMINISTRADOR o SUPER USUARIO puede corregir un activo ya registrado."));
        }

        try {
            Long id = Long.parseLong(Encriptar.decrypt(idEnc));
            Activo a = activoService.findById(id);

            if (a == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Activo no encontrado."));
            }

            String estado = a.getEstado() == null ? "" : a.getEstado().toUpperCase();
            if (!"ACTIVO".equals(estado) && !"PENDIENTE".equals(estado)) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "No se puede editar un activo en estado " + estado + "."));
            }

            // Foto previa: es lo que se compara para describir el cambio en el historial.
            String antes = resumenActivo(a);
            String codigoOriginal = a.getCodigo();   // llave del DBF: no cambia acá
            boolean estabaPublicado = "ACTIVO".equals(estado);

            // Las referencias de ANTES hay que guardarlas acá, no después: aplicarCambios
            // reemplaza las del activo y el historial terminaba anotando la misma oficina
            // y el mismo responsable en las dos columnas, sin decir de dónde a dónde fue.
            Oficina     oficinaAnterior     = a.getOficina();
            Responsable responsableAnterior = a.getResponsable();

            aplicarCambios(a, req);
            a.setFecMod(LocalDate.now());
            a.setFechaUlt(LocalDate.now());
            a.setUsuMod(usuario.getUsuario());
            a.setModificacion(new Date());
            a.setModificacionIdUsuario(usuario.getIdUsuario());
            a.setApiEstado(Short.valueOf("3"));   // 3 = modificado, pendiente de reflejar
            activoService.save(a);

            String despues = resumenActivo(a);
            registrarHistorialEdicion(a, antes, despues, usuario, oficinaAnterior, responsableAnterior);

            // Un activo PENDIENTE todavía no existe en el VSIAF: no hay nada que actualizar.
            if (!estabaPublicado) {
                return ResponseEntity.ok(Map.of("ok", true, "vsiaf", "NO_APLICA",
                        "msg", "Cambios guardados. El activo sigue PENDIENTE, así que no se envió nada al VSIAF."));
            }

            if (a.getOficina() == null || a.getOficina().getPredio() == null
                    || a.getOficina().getPredio().getEntidad() == null) {
                log.warn("[EDITAR-REGISTRADO] Activo {} sin datos de oficina/predio/entidad: no se encoló el UPDATE.",
                        codigoOriginal);
                return ResponseEntity.ok(Map.of("ok", true, "vsiaf", "ERROR",
                        "msg", "Guardado en la base, pero NO se actualizó el VSIAF: al activo le faltan datos de "
                             + "oficina, predio o entidad. El VSIAF quedó con la información anterior."));
            }

            try {
                // Si la corrección cambió el auxiliar, el nuevo puede no existir todavía en
                // AUXILIAR.DBF: mandarlo primero evita que el activo quede con un CODAUX huérfano.
                String fallaAux = sincronizarAuxiliarDelActivo(a, usuario.getUsuario());

                actualDbfWriterService.actualizarDesdeActivo(
                        codigoOriginal, a,
                        a.getOficina().getPredio().getEntidad().getEntidadCodigo(),
                        a.getOficina().getPredio().getUnidad(),
                        usuario.getUsuario());

                marcarEnvioAlVsiaf(a);
                notificarCambioPendientes("edicion-registrada");
                if (fallaAux != null) {
                    return ResponseEntity.ok(Map.of("ok", true, "vsiaf", "PARCIAL",
                            "msg", "Cambios enviados al VSIAF, pero el auxiliar NO se pudo enviar ("
                                 + fallaAux + "): en el VSIAF el activo va a verse sin auxiliar."));
                }
                return ResponseEntity.ok(Map.of("ok", true, "vsiaf", estadoEnvio(),
                        "msg", "Cambios guardados. " + detalleEnvio()));

            } catch (Exception e) {
                // Importante no cantar victoria: la base cambió y el VSIAF no. Quien
                // corrige tiene que enterarse para no dar el dato por sincronizado.
                log.error("[EDITAR-REGISTRADO] Falló el UPDATE al VSIAF del activo {}: {}",
                        codigoOriginal, e.getMessage());
                a.setSincVsiaf(Activo.SINC_ERROR);
                a.setSincVsiafMensaje("No se pudo dejar la orden para el VSIAF: " + e.getMessage());
                a.setSincVsiafFecha(LocalDateTime.now());
                return ResponseEntity.ok(Map.of("ok", true, "vsiaf", "ERROR",
                        "msg", "Guardado en la base, pero FALLÓ el envío al VSIAF: " + e.getMessage()
                             + ". Los dos sistemas quedaron distintos: revisá la cola o usá Conciliación."));
            }

        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("[EDITAR-REGISTRADO] Error editando activo {}", idEnc, e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
        }
    }

    /** Línea legible con los datos que la corrección puede tocar, para comparar antes/después. */
    private String resumenActivo(Activo a) {
        return String.format(
                "desc='%s' | costo=%s | vidaUtil=%s | fechaAdq=%s | grupo=%s | aux=%s | oficina=%s | resp=%s | finan=%s",
                a.getDescripcion(),
                a.getCosto(),
                a.getVidaUtil(),
                a.getFechaAdquisicion(),
                a.getGrupoContable() != null ? a.getGrupoContable().getNombre() : "—",
                a.getAuxiliar() != null ? a.getAuxiliar().getNombre() : "—",
                a.getOficina() != null ? a.getOficina().getNombre() : "—",
                a.getResponsable() != null && a.getResponsable().getPersona() != null
                        ? a.getResponsable().getPersona().getNombreCompleto() : "—",
                a.getOrganismoFinanciero() != null ? a.getOrganismoFinanciero().getDescripcion() : "—");
    }

    /**
     * Deja la corrección en {@code historial_activo}. No debe romper el flujo si falla.
     * <p>
     * La oficina y el responsable de ANTES llegan por parámetro porque para cuando se
     * llama a este método el activo ya tiene los valores nuevos: leerlos de la entidad
     * daba las dos columnas iguales y el historial no servía para reconstruir un
     * movimiento.
     */
    private void registrarHistorialEdicion(Activo activo, String antes, String despues, Usuario usuario,
                                           Oficina oficinaAnterior, Responsable responsableAnterior) {
        try {
            if (antes.equals(despues)) return;   // no hubo cambios reales que registrar

            HistorialActivo h = new HistorialActivo();
            h.setActivo(activo);
            h.setCodigoActivo(activo.getCodigo());
            h.setTipoEvento("EDICION");
            h.setFechaEvento(LocalDateTime.now());
            h.setIdUsuario(usuario.getIdUsuario());
            h.setNombreUsuario(usuario.getUsuario());
            h.setDescripcionEvento("Corrección desde el historial de asignaciones.\nANTES:  " + antes
                                 + "\nDESPUÉS: " + despues);

            if (oficinaAnterior != null) {
                h.setOficinaAnterior(oficinaAnterior);
                h.setNombreOficinaAnterior(oficinaAnterior.getNombre());
            }
            if (activo.getOficina() != null) {
                h.setOficinaNueva(activo.getOficina());
                h.setNombreOficinaNueva(activo.getOficina().getNombre());
            }
            if (responsableAnterior != null) {
                h.setResponsableAnterior(responsableAnterior);
                h.setNombreRespAnterior(nombreDe(responsableAnterior));
            }
            if (activo.getResponsable() != null) {
                h.setResponsableNuevo(activo.getResponsable());
                h.setNombreRespNuevo(nombreDe(activo.getResponsable()));
            }
            historialActivoDao.save(h);
        } catch (Exception e) {
            log.warn("[EDITAR-REGISTRADO] No se pudo registrar el historial de la edición: {}", e.getMessage());
        }
    }

    /** Nombre del responsable para el snapshot del historial; nunca revienta por datos incompletos. */
    private String nombreDe(Responsable r) {
        return (r != null && r.getPersona() != null) ? r.getPersona().getNombreCompleto() : null;
    }

    @ValidarUsuarioAutenticado
    @PostMapping(value = "/api/editar-lote",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> editarLote(
            @RequestBody EditarLoteRequest req,
            HttpServletRequest httpReq) {

        if (req.getActivos() == null || req.getActivos().isEmpty())
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Sin activos."));

        String usuNombre = obtenerUsuario(httpReq);
        int actualizados = 0;
        List<String> errores = new ArrayList<>();

        for (EditarActivoPendienteRequest item : req.getActivos()) {
            try {
                Long id = Long.parseLong(Encriptar.decrypt(item.getIdEnc()));
                Activo a = activoService.findById(id);

                if (a == null || !"PENDIENTE".equalsIgnoreCase(a.getEstado())) {
                    errores.add("Activo no encontrado o no está PENDIENTE: " + item.getIdEnc());
                    continue;
                }

                aplicarCambios(a, item);
                a.setFecMod(LocalDate.now());
                a.setUsuMod(usuNombre);
                activoService.save(a);
                actualizados++;

            } catch (IllegalArgumentException datoInvalido) {
                // p. ej. auxiliar de otro predio/grupo: el motivo sirve tal cual al usuario
                errores.add(datoInvalido.getMessage());
            } catch (Exception e) {
                log.error("Error en editar-lote para idEnc {}: {}", item.getIdEnc(), e.getMessage());
                errores.add("Error en un activo: " + e.getMessage());
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("actualizados", actualizados);
        resp.put("errores", errores.size());
        if (!errores.isEmpty())
            resp.put("detallesError", errores);

        resp.put("msg", String.format("Lote actualizado: %d activo(s). Errores: %d.", actualizados, errores.size()));
        if (actualizados > 0) notificarCambioPendientes("edicion-lote");
        return ResponseEntity.ok(resp);
    }

    private void aplicarCambios(Activo a, EditarActivoPendienteRequest req) {

        /*
         * El auxiliar se resuelve PRIMERO, contra el grupo y la oficina que va a tener el
         * activo después de la edición (no contra los que tiene ahora). Se valida antes de
         * tocar nada porque en `editarRegistrado` el método es @Transactional y la entidad
         * está gestionada: si se mutara y después se rechazara el auxiliar, el dirty
         * checking igual guardaría los campos ya cambiados.
         */
        GrupoContable grupoFinal = (req.getIdGrupoContable() != null)
                ? grupoContableService.findById(req.getIdGrupoContable())
                : a.getGrupoContable();
        Oficina oficinaFinal = (req.getIdOficina() != null)
                ? oficinaService.findById(req.getIdOficina())
                : a.getOficina();
        Auxiliar auxiliarFinal = resolverAuxiliarCoherente(req.getIdAuxiliar(), oficinaFinal, grupoFinal);

        if (req.getDescripcion() != null && !req.getDescripcion().isBlank()) {
            String desc = req.getDescripcion().trim();
            a.setDescripcion(desc.length() > 1024 ? desc.substring(0, 1024) : desc);
        }
        // Se aplica DESPUÉS de la descripción, sobre el texto ya definitivo. Es idempotente,
        // así que da igual si el front ya la escribió en el textarea: no se duplica.
        if (req.getIncluyeAccesorios() != null) {
            String desc = aplicarMarcaAccesorios(a.getDescripcion(), req.getIncluyeAccesorios());
            a.setDescripcion(desc.length() > 1024 ? desc.substring(0, 1024) : desc);
        }
        if (req.getCosto() != null)
            a.setCosto(req.getCosto());
        if (req.getVidaUtil() != null)
            a.setVidaUtil(BigDecimal.valueOf(req.getVidaUtil()));
        if (req.getFechaAdquisicion() != null)
            a.setFechaAdquisicion(req.getFechaAdquisicion());
        if (req.getObserv() != null)
            a.setObserv(req.getObserv());

        if (req.getIdGrupoContable() != null)
            a.setGrupoContable(grupoFinal);

        if (req.getIdOficina() != null)
            a.setOficina(oficinaFinal);

        if (req.getIdResponsable() != null)
            a.setResponsable(responsableService.findById(req.getIdResponsable()));

        a.setAuxiliar(auxiliarFinal);   // null cuando el request no manda idAuxiliar = limpiar

        if (req.getIdOrganismoFinanciero() != null) {
            OrganismoFinanciero orgFin =
                organismoFinancieroService.findById(req.getIdOrganismoFinanciero());
            a.setOrganismoFinanciero(orgFin);
            a.setOrgFinCode(orgFin != null ? orgFin.getCodOf() : null);
        } else {
            a.setOrganismoFinanciero(null);
            a.setOrgFinCode(null);
        }
    }

    /** Texto que se agrega al final de la descripción cuando el activo incluye accesorios. */
    public static final String MARCA_ACCESORIOS = "INCLUYE ACCESORIOS";

    /**
     * Agrega o quita {@link #MARCA_ACCESORIOS} al final de la descripción.
     * Es idempotente: agregar dos veces no duplica el texto, y quitar cuando no está
     * devuelve la descripción intacta. El {@code while} limpia duplicados que hayan
     * quedado de ediciones manuales previas.
     */
    private String aplicarMarcaAccesorios(String descripcion, boolean incluir) {
        String base = descripcion == null ? "" : descripcion.trim();

        while (base.toUpperCase().endsWith(MARCA_ACCESORIOS)) {
            base = base.substring(0, base.length() - MARCA_ACCESORIOS.length()).trim();
        }

        if (!incluir)          return base;
        if (base.isEmpty())    return MARCA_ACCESORIOS;
        return base + " " + MARCA_ACCESORIOS;
    }

    private String obtenerUsuario(HttpServletRequest req) {
        Usuario u = (Usuario) req.getSession().getAttribute("usuario");
        return u != null ? u.getUsuario() : "SISTEMA";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/eliminar/{id_activo}")
    public ResponseEntity<String> eliminar(Model model, @PathVariable("id_activo") String idActivo) throws Exception {
        Long id = Long.parseLong(Encriptar.decrypt(idActivo));
        Activo activo = activoService.findById(id);
        activo.setEstado("ELIMINADO");
        activoService.save(activo);
        return ResponseEntity.ok("Registro Eliminado");
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/sync-forzar")
    @ResponseBody
    public ResponseEntity<?> forzarSync() {
        try {
            log.info("🔄 Sync forzado de activos solicitado");
            ResponseEntity<?> resultado = activoSyncService.syncFromMounted(null, true);
            return ResponseEntity.ok(Map.of(
                "ok",      true,
                "mensaje", "Sync forzado completado",
                "detalle", resultado.getBody()
            ));
        } catch (Exception e) {
            log.error("Error en sync forzado: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("ok", false, "msg", e.getMessage()));
        }
    }

    /* =============================== */
    /* ========== PENDIENTES ========= */
    /* =============================== */

    @ValidarUsuarioAutenticado
    @GetMapping("/vistap")
    public String vista_activo_pendiente(Model model) {
        model.addAttribute("grupos", grupoContableService.listarGruposContables());
        return "activo/vista_pendientes";
    }

    @PostMapping("/tabla-registros_pendientes")
    public String tabla_registro_pendiente(Model model) throws Exception {

        List<AsignacionActivo> asignaciones = asignacionActivoService
            .listarConDetalles();

        List<AsignacionPendienteDTO> dtos = new ArrayList<>();
        for (AsignacionActivo asig : asignaciones) {
            AsignacionPendienteDTO dto = new AsignacionPendienteDTO();
            dto.setAsignacion(asig);
            dto.setEncryptedAsignacionId(
                Encriptar.encrypt(String.valueOf(asig.getIdAsignacionActivo())));

            List<ActivoPendienteItemDTO> items = new ArrayList<>();
            for (DetalleAsignacionActivo det : asig.getDetalles()) {
                if (!"PENDIENTE".equalsIgnoreCase(det.getActivo().getEstado())) continue;
                ActivoPendienteItemDTO item = new ActivoPendienteItemDTO();
                item.setActivo(det.getActivo());
                item.setEncryptedActivoId(
                    Encriptar.encrypt(String.valueOf(det.getActivo().getIdActivo())));
                item.setCodigoSnapshot(det.getCodigoActivoSnapshot());
                List<String> faltantes = camposFaltantesVsiaf(det.getActivo());
                item.setFaltantes(faltantes);
                item.setCompleto(faltantes.isEmpty());
                items.add(item);
            }

            dto.setItems(items);
            dto.setTotalActivos((int) asig.getDetalles().stream()
                .filter(d -> !"CANCELADO".equalsIgnoreCase(d.getActivo().getEstado()))
                .count());
            dto.setTotalPendientes(items.size());
            dto.setTotalSincronizados(
                asig.getDetalles().stream()
                    .filter(d -> "ACTIVO".equalsIgnoreCase(d.getActivo().getEstado()))
                    .count());
            dtos.add(dto);
        }

        List<Activo> sinAsignacion = activoService.listarActivosPendientes();
        List<ActivoPendienteItemDTO> sinAsignacionItems = new ArrayList<>();
        for (Activo a : sinAsignacion) {
            ActivoPendienteItemDTO item = new ActivoPendienteItemDTO();
            item.setActivo(a);
            item.setEncryptedActivoId(Encriptar.encrypt(String.valueOf(a.getIdActivo())));
            List<String> faltantes = camposFaltantesVsiaf(a);
            item.setFaltantes(faltantes);
            item.setCompleto(faltantes.isEmpty());
            sinAsignacionItems.add(item);
        }

        long sinAsignacionIncompletos = sinAsignacionItems.stream().filter(i -> !i.isCompleto()).count();

        // Mismos totales de costo que en las asignaciones, pero para el bloque suelto
        // (ese grupo no tiene AsignacionPendienteDTO donde calcularlos).
        BigDecimal sinAsignacionCostoTotal = sinAsignacionItems.stream()
            .map(ActivoPendienteItemDTO::getActivo)
            .filter(a -> a.getCosto() != null && a.getCosto() > 0)
            .map(a -> BigDecimal.valueOf(a.getCosto()))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);

        List<String> sinAsignacionCodigosSinCosto = sinAsignacionItems.stream()
            .map(ActivoPendienteItemDTO::getActivo)
            .filter(a -> a.getCosto() == null || a.getCosto() <= 0)
            .map(a -> a.getCodigo() == null ? "(sin código)" : a.getCodigo())
            .toList();

        model.addAttribute("asignaciones", dtos);
        model.addAttribute("sinAsignacionItems", sinAsignacionItems);
        model.addAttribute("sinAsignacionIncompletos", sinAsignacionIncompletos);
        model.addAttribute("sinAsignacionCostoTotal", sinAsignacionCostoTotal);
        model.addAttribute("sinAsignacionCodigosSinCosto", sinAsignacionCodigosSinCosto);
        return "activo/tabla_registros_pendientes";
    }

    // public ResponseEntity<?> obtenerDetalle(@PathVariable String idEnc) {
    //     try {
    //         Long id = Long.valueOf(Encriptar.decrypt(idEnc));
    
    @ValidarUsuarioAutenticado
    @GetMapping("/api/detalle/{idEnc}")
    @ResponseBody
    public ResponseEntity<?> detalleActivo(@PathVariable String idEnc) {
        try {
            // 1. Desencriptación y búsqueda
            Long id = Long.valueOf(Encriptar.decrypt(idEnc));
            Activo a = activoService.findById(id);
            
            if (a == null) {
                return ResponseEntity.notFound().build();
            }

            // Usamos LinkedHashMap para mantener el orden de los campos en el JSON
            Map<String, Object> r = new LinkedHashMap<>();

            // ── Campos básicos ─────────────────────────────────────
            r.put("idActivo",         a.getIdActivo());
            r.put("codigo",           a.getCodigo());
            r.put("codigoSec",        a.getCodigoSec()); // <-- Agregado
            r.put("descripcion",      a.getDescripcion());
            r.put("costo",            a.getCosto());
            r.put("vidaUtil",         a.getVidaUtil());
            r.put("fechaAdquisicion", a.getFechaAdquisicion() != null 
                                    ? a.getFechaAdquisicion().toString() : null);
            r.put("estado",           a.getEstado());
            r.put("observ",           a.getObserv());

            // ── Grupo Contable ─────────────────────────────────────
            if (a.getGrupoContable() != null) {
                Map<String, Object> grp = new LinkedHashMap<>();
                grp.put("idGrupoContable", a.getGrupoContable().getIdGrupoContable());
                grp.put("nombre",          a.getGrupoContable().getNombre());
                r.put("grupoContable", grp);
            }

            // ── Auxiliar ───────────────────────────────────────────
            if (a.getAuxiliar() != null) {
                Map<String, Object> aux = new LinkedHashMap<>();
                aux.put("idAuxiliar", a.getAuxiliar().getIdAuxiliar());
                aux.put("nombre",     a.getAuxiliar().getNombre());
                r.put("auxiliar", aux);
            }

            // ── Organismo Financiero ───────────────────────────────
            if (a.getOrganismoFinanciero() != null) {
                Map<String, Object> org = new LinkedHashMap<>();
                org.put("idOrganismoFinanciero", a.getOrganismoFinanciero().getIdOrganismoFinanciero());
                org.put("sigla",                 a.getOrganismoFinanciero().getSigla());
                org.put("descripcion",           a.getOrganismoFinanciero().getDescripcion());
                r.put("organismoFinanciero", org);
            }

            // ── Responsable (EL QUE FALTABA) ───────────────────────
            if (a.getResponsable() != null) {
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("idResponsable", a.getResponsable().getIdResponsable());
                if (a.getResponsable().getPersona() != null) {
                    Map<String, Object> pers = new LinkedHashMap<>();
                    pers.put("nombreCompleto", a.getResponsable().getPersona().getNombreCompleto());
                    resp.put("persona", pers);
                }
                r.put("responsable", resp);
            }

            // ── Oficina + Predio + Municipio ───────────────────────
            if (a.getOficina() != null) {
                Map<String, Object> ofi = new LinkedHashMap<>();
                ofi.put("idOficina", a.getOficina().getIdOficina());
                ofi.put("codOfi",    a.getOficina().getCodOfi());
                ofi.put("nombre",    a.getOficina().getNombre());
                r.put("oficina", ofi);

                // Campos de apoyo para la cascada en el Frontend
                if (a.getOficina().getPredio() != null) {
                    r.put("predioId", a.getOficina().getPredio().getIdPredio());

                    if (a.getOficina().getPredio().getMunicipio() != null) {
                        r.put("municipioId", a.getOficina().getPredio().getMunicipio().getIdMunicipio());
                    }
                }
            }

            return ResponseEntity.ok(r);

        } catch (Exception e) {
            log.error("Error al obtener detalle: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("ok", false, "message", "Error: " + e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/aprobar/{idEnc}")
    @ResponseBody
    public Map<String, Object> aprobarActivo(@PathVariable String idEnc,
            HttpServletRequest request) {
        
        try{
            Long id = Long.valueOf(Encriptar.decrypt(idEnc));
            Activo a = activoService.findById(id);

            if (a == null) return Map.of("ok", false, "message", "Activo no encontrado");
            if (!"PENDIENTE".equalsIgnoreCase(a.getEstado())) {
                return Map.of("ok", false, "message", "El activo no está en estado PENDIENTE.");
            }

            List<String> falt = camposFaltantesVsiaf(a);
            if (!falt.isEmpty()) {
                return Map.of("ok", false,
                    "message", "No se puede subir al VSIAF: datos incompletos (falta " + String.join(", ", falt) + ").");
            }

            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

            if (a.getOficina() == null || a.getOficina().getPredio() == null) {
                return Map.of("ok", false, "message", "Faltan datos de Oficina/Predio para sincronizar.");
            }

            String entidadCode = a.getOficina().getPredio().getEntidad().getEntidadCodigo();
            String unidadCode = a.getOficina().getPredio().getUnidad();

            // ── Dependencias ANTES del activo ─────────────────────────────────────────
            // ACTUAL.DBF guarda códigos (CODOFIC, CODRESP, CODAUX), no referencias: si el
            // activo llega antes que ellos, en el VSIAF apunta a filas que no existen y se
            // ve sin oficina / sin responsable / sin auxiliar. Todos los INSERT que encola
            // el worker son insert-if-not-exists, así que repetirlos no duplica nada.
            Oficina oficina = a.getOficina();
            if (oficina.getCodOfi() != null) {
                oficina.setApiEstado(Short.valueOf("1"));
                oficinaDbfWriterService.insertarDesdeOficina(oficina, entidadCode, unidadCode, usuarioNombre);
                oficinaService.save(oficina);
            }

            Responsable resp = a.getResponsable();
            if (resp != null && resp.getCodigoFuncionario() != null
                    && !resp.getCodigoFuncionario().replaceAll("\\D+", "").isEmpty()) {
                resp.setApiEstado(Short.valueOf("1"));
                respDbfWriterService.insertarDesdeResponsable(resp, entidadCode, unidadCode, usuarioNombre);
                responsableService.save(resp);
            }

            String fallaAux = sincronizarAuxiliarDelActivo(a, usuarioNombre);

            // El worker inserta solo si no existe (chequeo por índice); sin escaneo del DBF.
            actualDbfWriterService.insertarDesdeActivo(a, entidadCode, unidadCode, usuarioNombre);

            a.setEstado("ACTIVO");
            a.setApiEstado(Short.valueOf("1"));
            marcarEnvioAlVsiaf(a);
            activoService.save(a);

            notificarCambioPendientes("aprobacion");
            if (fallaAux != null) {
                return Map.of("ok", true, "id", id, "vsiaf", "PARCIAL",
                    "message", "Activo aprobado, pero su auxiliar NO se pudo enviar al VSIAF (" + fallaAux
                             + "). En el VSIAF el activo va a verse sin auxiliar hasta que se resuelva.");
            }
            return Map.of("ok", true, "id", id, "vsiaf", estadoEnvio(),
                "message", "Activo aprobado. " + detalleEnvio());

        } catch (Exception e) {
            log.error("Error aprobando activo: {}", e.getMessage(), e);
            return Map.of("ok", false, "message", "Error al sincronizar con DBF: " + e.getMessage());
        }   
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/aprobar-masivo")
    @ResponseBody
    public ResponseEntity<?> aprobarMasivo(@RequestBody List<String> idsEnc, HttpServletRequest request) {
    
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
    
        int exitos = 0;
        int errores = 0;
        List<String> detallesError = new ArrayList<>();
    
        for (String idEnc : idsEnc) {
            try {
                Long id = Long.valueOf(Encriptar.decrypt(idEnc));
                Activo a = activoService.findById(id);
    
                if (a == null || !"PENDIENTE".equalsIgnoreCase(a.getEstado())) {
                    errores++;
                    continue;
                }
                // Bloqueo por datos obligatorios incompletos (no se sube al VSIAF)
                List<String> falt = camposFaltantesVsiaf(a);
                if (!falt.isEmpty()) {
                    errores++;
                    detallesError.add("Activo " + a.getCodigo() + ": datos incompletos (" + String.join(", ", falt) + ").");
                    continue;
                }
                if (a.getOficina() == null || a.getOficina().getPredio() == null) {
                    errores++;
                    detallesError.add("Activo " + (a != null ? a.getCodigo() : idEnc) + ": Faltan datos de Oficina/Predio.");
                    continue;
                }
    
                Predio predio = a.getOficina().getPredio();
    
                // ✅ UNIDAD = predio.unidad (campo textual: "CAUN", "CULP"…)
                //    NUNCA predio.codigo (numérico auxiliar del sistema)
                String unidadCode = (predio.getUnidad() != null) ? predio.getUnidad() : "";
    
                String entidadCode = "";
                if (predio.getEntidad() != null && predio.getEntidad().getEntidadCodigo() != null) {
                    entidadCode = predio.getEntidad().getEntidadCodigo();
                }
    
                // ── Sincronizar Oficina (el worker inserta solo si no existe; sin escaneo DBF) ──
                Oficina oficina = a.getOficina();
                Short codOfic = oficina.getCodOfi();
                if (codOfic != null) {
                    oficina.setApiEstado(Short.valueOf("1"));
                    oficinaDbfWriterService.insertarDesdeOficina(oficina, entidadCode, unidadCode, usuarioNombre);
                    oficinaService.save(oficina);
                }

                // ── Sincronizar Responsable (el worker inserta solo si no existe; sin escaneo DBF) ──
                Responsable resp = a.getResponsable();
                if (resp != null && resp.getCodigoFuncionario() != null
                        && !resp.getCodigoFuncionario().replaceAll("\\D+", "").isEmpty()) {
                    resp.setApiEstado(Short.valueOf("1"));
                    respDbfWriterService.insertarDesdeResponsable(resp, entidadCode, unidadCode, usuarioNombre);
                    responsableService.save(resp);
                }

                // ── Sincronizar Auxiliar ANTES del activo ─────────────────────────────
                // ACTUAL.DBF guarda el CODAUX, no una referencia: si el auxiliar todavía no
                // está en AUXILIAR.DBF, el activo llega al VSIAF apuntando a la nada y se ve
                // sin auxiliar. El INSERT es insert-if-not-exists: repetirlo no duplica.
                String fallaAux = sincronizarAuxiliarDelActivo(a, usuarioNombre);
                if (fallaAux != null) {
                    detallesError.add("Activo " + a.getCodigo() + ": se subió, pero su auxiliar NO llegó al VSIAF ("
                            + fallaAux + ").");
                }

                // ── Sincronizar Activo (el worker inserta solo si no existe; sin escaneo DBF) ──
                actualDbfWriterService.insertarDesdeActivo(a, entidadCode, unidadCode, usuarioNombre);

                a.setEstado("ACTIVO");
                a.setApiEstado(Short.valueOf("1"));
                marcarEnvioAlVsiaf(a);
                activoService.save(a);
                exitos++;
    
            } catch (Exception e) {
                log.error("[APROBAR] Error procesando id {}: {}", idEnc, e.getMessage());
                errores++;
                detallesError.add("Error en un activo: " + e.getMessage());
            }
        }
    
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("exitos", exitos);
        result.put("errores", errores);
        result.put("detalles", detallesError);
        result.put("vsiaf", estadoEnvio());
        result.put("msg", String.format("Proceso finalizado. Éxitos: %d | Errores: %d. %s",
                exitos, errores, exitos > 0 ? detalleEnvio() : ""));

        if (exitos > 0) notificarCambioPendientes("aprobacion-masiva");
        return ResponseEntity.ok(result);
    }

    /**
     * Cancela activos PENDIENTES (que nunca se subieron al VSIAF): los marca como
     * CANCELADO y LIBERA su código — lo respalda en {@code codigoAnulado} y deja
     * {@code codigo = NULL}, para que ese correlativo vuelva a estar disponible para
     * nuevos registros. No toca el VSIAF. Es irreversible.
     *
     * Requiere la migración de la Fase 2 aplicada (codigo nullable + UNIQUE); de lo
     * contrario, dejar codigo en NULL fallará por la restricción NOT NULL de la BD.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/api/cancelar-pendientes")
    @ResponseBody
    public ResponseEntity<?> cancelarPendientes(@RequestBody List<String> idsEnc, HttpServletRequest request) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

        int cancelados = 0;
        int errores = 0;
        List<String> codigosLiberados = new ArrayList<>();
        List<String> detallesError = new ArrayList<>();

        for (String idEnc : idsEnc) {
            try {
                Long id = Long.valueOf(Encriptar.decrypt(idEnc));
                Activo a = activoService.findById(id);

                if (a == null) { errores++; continue; }
                if (!"PENDIENTE".equalsIgnoreCase(a.getEstado())) {
                    errores++;
                    detallesError.add("El activo " + (a.getCodigo() != null ? a.getCodigo() : idEnc)
                        + " ya no está PENDIENTE; no se canceló.");
                    continue;
                }

                String original = a.getCodigo();
                a.setCodigoAnulado(original);   // respaldo para auditoría
                a.setCodigo(null);              // libera el correlativo
                a.setEstado("CANCELADO");
                a.setApiEstado(Short.valueOf("0"));   // no va al DBF
                a.setFecMod(LocalDate.now());
                a.setFechaUlt(LocalDate.now());
                a.setUsuMod(usuarioNombre);

                activoService.save(a);
                cancelados++;
                if (original != null) codigosLiberados.add(original);

            } catch (Exception e) {
                log.error("[CANCELAR] Error cancelando id {}: {}", idEnc, e.getMessage());
                errores++;
                detallesError.add("Error en un activo: " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("cancelados", cancelados);
        result.put("errores", errores);
        result.put("codigosLiberados", codigosLiberados);
        result.put("detalles", detallesError);
        result.put("msg", "Se cancelaron " + cancelados + " activo(s)."
            + (cancelados > 0 ? " Sus códigos quedaron disponibles para nuevos registros." : "")
            + (errores > 0 ? " (" + errores + " no se pudieron cancelar)" : ""));
        if (cancelados > 0) notificarCambioPendientes("cancelacion");
        return ResponseEntity.ok(result);
    }

    /**
     * Registra UN activo en un código puntual (un "hueco" libre de la serie), elegido
     * desde el módulo de revisión de correlativos. El código viene fijo; se valida que
     * su prefijo corresponda al predio/grupo de la oficina elegida y que no esté tomado.
     * El índice único uk_activo_codigo es la red final ante carreras de concurrencia.
     * El activo queda PENDIENTE (igual que el registro normal).
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-en-hueco")
    @ResponseBody
    public ResponseEntity<?> registrarEnHueco(@RequestBody RegistroHuecoRequest req, HttpServletRequest httpReq) {
        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
        try {
            String codigo = (req.getCodigo() != null) ? req.getCodigo().trim() : "";
            if (!codigo.matches("^[^-]+-[^-]+-[0-9]+-[0-9]+$")) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Código con formato inválido."));
            }
            if (req.getIdOficina() == null || req.getIdResponsable() == null || req.getIdGrupoContable() == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Oficina, responsable y grupo son obligatorios."));
            }
            if (req.getDescripcion() == null || req.getDescripcion().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "La descripción es obligatoria."));
            }

            Oficina oficina = oficinaService.findById(req.getIdOficina());
            Responsable responsable = responsableService.findById(req.getIdResponsable());
            GrupoContable grupo = grupoContableService.findById(req.getIdGrupoContable());
            if (oficina == null || responsable == null || grupo == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Oficina, responsable o grupo inexistente."));
            }
            if (oficina.getPredio() == null || oficina.getPredio().getMunicipio() == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "La oficina no tiene predio/municipio configurado."));
            }

            // El prefijo del código debe corresponder al predio/municipio de la oficina y al grupo.
            String prefijoEsperado = String.join("-",
                    oficina.getPredio().getMunicipio().getCodigo(),
                    oficina.getPredio().getCodigo(),
                    String.format("%02d", grupo.getCodDbf()));
            String prefijoCodigo = codigo.substring(0, codigo.lastIndexOf('-'));
            if (!prefijoEsperado.equals(prefijoCodigo)) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "El código no corresponde al predio/grupo de la oficina elegida (esperado " + prefijoEsperado + ")."));
            }

            // ¿el código ya está tomado? (el UNIQUE atrapa la carrera; esto da un mensaje claro)
            if (activoService.findByCodigo(codigo).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false,
                        "msg", "El código " + codigo + " ya fue tomado. Elige otro hueco."));
            }

            Auxiliar auxiliar = resolverAuxiliarCoherente(req.getIdAuxiliar(), oficina, grupo);
            OrganismoFinanciero orgFin = (req.getIdOrganismoFinanciero() != null)
                    ? organismoFinancieroService.findById(req.getIdOrganismoFinanciero()) : null;

            Activo a = new Activo();
            a.setCodigo(codigo);
            a.setDescripcion(req.getDescripcion().trim().toUpperCase());
            if (req.getFechaAdquisicion() != null && !req.getFechaAdquisicion().isBlank()) {
                a.setFechaAdquisicion(LocalDate.parse(req.getFechaAdquisicion()));
            }
            a.setVidaUtil(req.getVidaUtil() != null ? BigDecimal.valueOf(req.getVidaUtil()) : BigDecimal.ZERO);
            a.setCosto(req.getCosto() != null ? req.getCosto() : 0.0);
            a.setResponsable(responsable);
            a.setOrganismoFinanciero(orgFin);
            if (orgFin != null) a.setOrgFinCode(orgFin.getCodOf());
            a.setGrupoContable(grupo);
            a.setOficina(oficina);
            a.setAuxiliar(auxiliar);
            a.setEstado("PENDIENTE");
            a.setApiEstado(Short.valueOf("3"));
            a.setVidaUtilAnterior(0);
            a.setEstadoActivo(estadoActivoService.findById(1L));
            a.setCostoAnterior(0.0);
            a.setDepreciacionAcum(0.0);
            a.setUsuario(usuarioNombre);
            a.setFecMod(LocalDate.now());
            a.setFechaUlt(LocalDate.now());

            activoService.save(a);

            notificarCambioPendientes("registro-hueco");
            return ResponseEntity.ok(Map.of("ok", true, "codigo", codigo,
                    "msg", "Activo registrado con el código " + codigo + " (queda PENDIENTE)."));

        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            return ResponseEntity.badRequest().body(Map.of("ok", false,
                    "msg", "El código acaba de ser tomado por otro registro. Elige otro hueco."));
        } catch (IllegalArgumentException datoInvalido) {
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", datoInvalido.getMessage()));
        } catch (Exception e) {
            log.error("Error registrando en hueco", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    /**
     * Registra en LOTE varios activos en huecos de una misma serie. Todos comparten
     * oficina + responsable + grupo (modelo confirmado con el área), de modo que luego
     * se puede emitir UN acta de asignación reutilizando {@code /reportes/generar-asignacion}
     * con los ids devueltos en {@code idsParaReporte} (igual que el registro masivo).
     *
     * Valida ítem por ítem: formato del código, prefijo == predio/grupo de la oficina y
     * que el código esté libre. Si algún ítem falla, se omite y se reporta, pero los
     * válidos sí se registran. Cada activo queda PENDIENTE.
     */
    @ValidarUsuarioAutenticado
    @PostMapping("/registrar-huecos-lote")
    @ResponseBody
    public ResponseEntity<?> registrarHuecosLote(@RequestBody RegistroHuecosLoteRequest req,
                                                 HttpServletRequest httpReq) {
        Usuario usuario = (Usuario) httpReq.getSession().getAttribute("usuario");
        String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";
        try {
            if (req.getItems() == null || req.getItems().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "No hay huecos seleccionados."));
            }
            if (req.getIdOficina() == null || req.getIdResponsable() == null || req.getIdGrupoContable() == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Oficina, responsable y grupo son obligatorios."));
            }

            Oficina oficina = oficinaService.findById(req.getIdOficina());
            Responsable responsable = responsableService.findById(req.getIdResponsable());
            GrupoContable grupo = grupoContableService.findById(req.getIdGrupoContable());
            if (oficina == null || responsable == null || grupo == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Oficina, responsable o grupo inexistente."));
            }
            if (oficina.getPredio() == null || oficina.getPredio().getMunicipio() == null) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "La oficina no tiene predio/municipio configurado."));
            }

            String prefijoEsperado = String.join("-",
                    oficina.getPredio().getMunicipio().getCodigo(),
                    oficina.getPredio().getCodigo(),
                    String.format("%02d", grupo.getCodDbf()));

            OrganismoFinanciero orgFin = (req.getIdOrganismoFinanciero() != null)
                    ? organismoFinancieroService.findById(req.getIdOrganismoFinanciero()) : null;
            LocalDate fechaAdq = (req.getFechaAdquisicion() != null && !req.getFechaAdquisicion().isBlank())
                    ? LocalDate.parse(req.getFechaAdquisicion()) : null;

            // El auxiliar es POR ACTIVO; cacheo los resueltos para no repetir lookups.
            Map<Long, Auxiliar> auxCache = new HashMap<>();

            List<String> idsReporte = new ArrayList<>();
            List<String> errores = new ArrayList<>();
            java.util.Set<String> vistos = new java.util.HashSet<>();   // evita códigos repetidos en el mismo payload
            int totalCreados = 0;

            for (RegistroHuecosLoteRequest.Item it : req.getItems()) {
                String codigo = (it.getCodigo() != null) ? it.getCodigo().trim() : "";
                try {
                    if (!codigo.matches("^[^-]+-[^-]+-[0-9]+-[0-9]+$")) {
                        errores.add("Código con formato inválido: " + codigo); continue;
                    }
                    if (!vistos.add(codigo)) {
                        errores.add("Código repetido en la selección: " + codigo); continue;
                    }
                    String prefijoCodigo = codigo.substring(0, codigo.lastIndexOf('-'));
                    if (!prefijoEsperado.equals(prefijoCodigo)) {
                        errores.add("El código " + codigo + " no corresponde al predio/grupo de la oficina (esperado " + prefijoEsperado + ").");
                        continue;
                    }
                    if (it.getDescripcion() == null || it.getDescripcion().trim().isEmpty()) {
                        errores.add("Falta la descripción del activo con código " + codigo + "."); continue;
                    }
                    if (activoService.findByCodigo(codigo).isPresent()) {
                        errores.add("El código " + codigo + " ya fue tomado."); continue;
                    }

                    String descFinal = construirDescripcionActivo(
                            it.getDescripcion(), it.getColor(), it.getMarca(),
                            it.getModelo(), it.getSerie(), it.isIncluyeAccesorio());

                    // Auxiliar por activo (opcional). Se valida que sea del predio de la
                    // oficina y del grupo del lote: el CODAUX no es global.
                    Auxiliar auxiliar = null;
                    if (it.getIdAuxiliar() != null) {
                        try {
                            auxiliar = auxCache.computeIfAbsent(it.getIdAuxiliar(),
                                    idAux -> resolverAuxiliarCoherente(idAux, oficina, grupo));
                        } catch (IllegalArgumentException datoInvalido) {
                            errores.add("Código " + codigo + ": " + datoInvalido.getMessage());
                            continue;
                        }
                    }

                    Activo a = new Activo();
                    a.setCodigo(codigo);
                    a.setDescripcion(descFinal);
                    a.setFechaAdquisicion(fechaAdq);
                    a.setVidaUtil(req.getVidaUtil() != null ? BigDecimal.valueOf(req.getVidaUtil()) : BigDecimal.ZERO);
                    a.setCosto(req.getCosto() != null ? req.getCosto() : 0.0);
                    a.setResponsable(responsable);
                    a.setOrganismoFinanciero(orgFin);
                    if (orgFin != null) a.setOrgFinCode(orgFin.getCodOf());
                    a.setGrupoContable(grupo);
                    a.setOficina(oficina);
                    a.setAuxiliar(auxiliar);
                    a.setEstado("PENDIENTE");
                    a.setApiEstado(Short.valueOf("3"));
                    a.setVidaUtilAnterior(0);
                    a.setEstadoActivo(estadoActivoService.findById(1L));
                    a.setCostoAnterior(0.0);
                    a.setDepreciacionAcum(0.0);
                    a.setUsuario(usuarioNombre);
                    a.setFecMod(LocalDate.now());
                    a.setFechaUlt(LocalDate.now());

                    activoService.save(a);
                    idsReporte.add(Encriptar.encrypt(String.valueOf(a.getIdActivo())));
                    totalCreados++;

                } catch (org.springframework.dao.DataIntegrityViolationException dup) {
                    errores.add("El código " + codigo + " acaba de ser tomado por otro registro.");
                } catch (Exception ex) {
                    errores.add("Error con el código " + codigo + ": " + ex.getMessage());
                }
            }

            if (totalCreados > 0) notificarCambioPendientes("registro-hueco-lote");

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", totalCreados > 0);
            resp.put("totalCreados", totalCreados);
            resp.put("totalErrores", errores.size());
            resp.put("errores", errores);
            resp.put("idsParaReporte", idsReporte);
            resp.put("msg", totalCreados > 0
                    ? ("Se registraron " + totalCreados + " activo(s) en lote (PENDIENTE)."
                        + (errores.isEmpty() ? "" : " " + errores.size() + " no se pudieron registrar."))
                    : "No se registró ningún activo. Revisá los errores.");
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            log.error("Error registrando huecos en lote", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/generar-correlativo", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> generar(
            @RequestParam String mun,
            @RequestParam String pred,
            @RequestParam String grp) {
        String codigo = funciones.previewCodigoPorCodes(mun, pred, grp);
        return Map.of("codigo", codigo);
    }

    @ValidarUsuarioAutenticado
    @GetMapping(value = "/buscar-por-codigo", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<ActivoFormDTO> buscarPorCodigo(@RequestParam("codigo") String codigo) {
        return activoService.fetchFullByCodigo(codigo)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ActivoFormDTO toDto(Activo a) {
        System.out.println("oficina=" + (a.getOficina() != null) +
                ", predio=" + (a.getOficina() != null && a.getOficina().getPredio() != null) +
                ", municipio="
                + (a.getOficina() != null && a.getOficina().getPredio() != null
                        && a.getOficina().getPredio().getMunicipio() != null)
                +
                ", grupo=" + (a.getGrupoContable() != null) +
                ", aux=" + (a.getAuxiliar() != null) +
                ", resp=" + (a.getResponsable() != null) +
                ", persona=" + (a.getResponsable() != null && a.getResponsable().getPersona() != null) +
                ", orgFin=" + a.getOrgFinCode());
        ActivoFormDTO dto = new ActivoFormDTO();

        dto.setId(a.getIdActivo());
        dto.setCodigo(a.getCodigo());
        dto.setDescripcion(a.getDescripcion());
        dto.setFechaAdquisicion(a.getFechaAdquisicion() != null ? a.getFechaAdquisicion().toString() : null);
        dto.setVidaUtil(a.getVidaUtil());
        dto.setCosto(a.getCosto());

        if (a.getGrupoContable() != null)
            dto.setGrupoContableId(a.getGrupoContable().getIdGrupoContable());

        if (a.getAuxiliar() != null) {
            dto.setAuxiliarId(a.getAuxiliar().getIdAuxiliar());
            dto.setAuxiliarNombre(a.getAuxiliar().getNombre());
        }

        if (a.getOficina() != null) {
            dto.setOficinaId(a.getOficina().getIdOficina());
            dto.setOficinaNombre(a.getOficina().getNombre());
            if (a.getOficina().getPredio() != null) {
                dto.setPredioId(a.getOficina().getPredio().getIdPredio());
                if (a.getOficina().getPredio().getMunicipio() != null) {
                    dto.setMunicipioId(a.getOficina().getPredio().getMunicipio().getIdMunicipio());
                }
            }
        }

        if (a.getResponsable() != null) {
            dto.setResponsableId(a.getResponsable().getIdResponsable());
            if (a.getResponsable().getPersona() != null) {
                dto.setResponsableNombre(a.getResponsable().getPersona().getNombreCompleto());
            }
        }

        if (a.getOrgFinCode() != null && !a.getOrgFinCode().isEmpty()) {
            try {
                OrganismoFinanciero orgFin = organismoFinancieroService.findByCodOf(a.getOrgFinCode())
                        .orElse(null);
                
                if (orgFin != null) {
                    dto.setOrganismoFinancieroId(orgFin.getIdOrganismoFinanciero());
                    dto.setOrganismoFinancieroNombre(orgFin.getSigla() != null ? orgFin.getSigla() : orgFin.getDescripcion());
                    System.out.println("✅ OrganismoFinanciero encontrado: " + orgFin.getSigla());
                } else {
                    System.out.println("⚠️ OrganismoFinanciero no encontrado para código: " + a.getOrgFinCode());
                }
            } catch (Exception e) {
                System.err.println("❌ Error buscando OrganismoFinanciero: " + e.getMessage());
            }
        }
        return dto;
    }
}