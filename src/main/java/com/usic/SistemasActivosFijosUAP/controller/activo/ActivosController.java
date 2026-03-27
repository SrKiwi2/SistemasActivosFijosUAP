package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.usic.SistemasActivosFijosUAP.model.dao.ITransferenciaDao;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoFormDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoPendienteItemDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.AsignacionPendienteDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.DataTablesResponse;
import com.usic.SistemasActivosFijosUAP.model.dto.DetalleRegistroItem;
import com.usic.SistemasActivosFijosUAP.model.dto.EditarActivoPendienteRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.EditarLoteRequest;
import com.usic.SistemasActivosFijosUAP.model.dto.RegistroMasivoRequest;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;
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

    private final IConfiguracionGestionService configuracionGestionService;
    private final IAsignacionActivoService asignacionActivoService;
    private final IDetalleAsignacionActivoService detalleAsignacionActivoService;

    private final TransferenciaService transferenciaService;
    private final ITransferenciaDao transferenciaDao;

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
        String fecha = params.get("fecha");

        PageRequest pageRequest = PageRequest.of(start / length, length);

        Page<Activo> pagina = activoService.buscarConFiltros(
                searchValue, codigo, responsableId, oficinaId, fecha, pageRequest);

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
    public String formulario_activo(Model model, Activo activo) {
        model.addAttribute("municipios", municipioService.findAll());
        model.addAttribute("predios", predioServicio.findAll());
        model.addAttribute("grupos", grupoContableService.listarGruposContables());
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        model.addAttribute("responsables", responsableService.listarResponsables());
        model.addAttribute("financiadores", organismoFinancieroService.findAll());
        model.addAttribute("auxiliares", auxiliarService.findAll());
        return "activo/formulario";
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
            for (int i = 0; i < cantidad; i++) {
                
                Activo nuevoActivo = new Activo();
                
                nuevoActivo.setDescripcion(activo.getDescripcion());
                nuevoActivo.setCosto(activo.getCosto());
                nuevoActivo.setVidaUtil(activo.getVidaUtil());
                nuevoActivo.setFechaAdquisicion(activo.getFechaAdquisicion());
                nuevoActivo.setObserv(activo.getObserv());
                
                nuevoActivo.setGrupoContable(activo.getGrupoContable());
                nuevoActivo.setOficina(activo.getOficina());
                nuevoActivo.setResponsable(activo.getResponsable());
                nuevoActivo.setOrganismoFinanciero(activo.getOrganismoFinanciero());
                nuevoActivo.setAuxiliar(activo.getAuxiliar());
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

            Map<String, Integer> incrementosLocales = new HashMap<>();

            for (DetalleRegistroItem item : request.getItems()) {

                if (item.getIdResponsable() == null) {
                    return ResponseEntity.badRequest().body(
                        Map.of("ok", false, "msg", "Falta responsable en uno de los ítems."));
                }
                Responsable responsable = responsableService.findById(item.getIdResponsable());
                
                GrupoContable grupo = grupoContableService.findById(item.getIdGrupoContable());
                Oficina oficina = oficinaService.findById(item.getIdOficina());
                Auxiliar auxiliar = (item.getIdAuxiliar() != null) ? auxiliarService.findById(item.getIdAuxiliar()) : null;
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

                for (int i = 0; i < item.getCantidad(); i++) {
                    Activo a = new Activo();
                    
                    String codigoFinal = construirCodigo(codMun, codPred, codGrup, correlativoActual);
                    correlativoActual++;

                    a.setCodigo(codigoFinal);
                    a.setDescripcion(item.getDescripcion().toUpperCase());
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

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "msg", "Se registraron " + totalCreados + " activos correctamente.",
                "idsParaReporte", idsReporte
            ));

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
            activoOriginal.setCodigoSec(activoForm.getCodigoSec());
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
                        return ResponseEntity.ok(Map.of("ok", true, "msg", "Actualizado en BD. No se sincronizó DBF por falta de datos de Oficina."));
                    }

                    String entidadCode = activoOriginal.getOficina().getPredio().getEntidad().getEntidadCodigo();
                    String unidadCode = activoOriginal.getOficina().getPredio().getUnidad();

                    actualDbfWriterService.actualizarDesdeActivo(
                        codigoOriginal,
                        activoOriginal,
                        entidadCode,
                        unidadCode,
                        usuarioNombre
                    );

                    return ResponseEntity.ok(Map.of("ok", true, "msg", "Activo actualizado correctamente en BD y DBF"));

                } catch (Exception e) {
                    log.error("Error sync DBF al modificar: {}", e.getMessage());
                    return ResponseEntity.ok(Map.of("ok", true, "msg", "Guardado en BD, pero falló DBF: " + e.getMessage()));
                }
            }

            return ResponseEntity.ok(Map.of("ok", true, "msg", "Modificación guardada (Estado: Pendiente)"));

        } catch (Exception e) {
            log.error("Error fatal modificando activo", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
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
        String nombreAux  = auxOrigen.getNombre().trim().toUpperCase();
    
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
    
        nuevoAux = auxiliarService.save(nuevoAux);
        log.info("[AUX] Nuevo auxiliar guardado en BD: ID={} '{}' CodAux={}",
            nuevoAux.getIdAuxiliar(), nombreAux, nextCod);
    
        // 3. Sincronizar con auxiliar.DBF
        try {
            String entidadCode = predioDestino.getEntidad() != null
                ? predioDestino.getEntidad().getEntidadCodigo() : "";
            String unidadCode  = predioDestino.getUnidad() != null
                ? predioDestino.getUnidad() : "";
    
            if (entidadCode.isBlank() || unidadCode.isBlank()) {
                log.warn("[AUX-DBF] Predio '{}' sin entidad/unidad — auxiliar creado en BD pero NO en DBF.",
                    predioDestino.getDescrip());
            } else {
                auxiliarDbfWriterService.insertarDesdeAuxiliar(
                    nuevoAux, entidadCode, unidadCode, usuNombre);
                log.info("[AUX-DBF] Auxiliar '{}' insertado en auxiliar.DBF (unidad='{}')",
                    nombreAux, unidadCode);
            }
        } catch (Exception e) {
            // No revertir: el auxiliar ya está en BD, el DBF se puede re-sincronizar después
            log.error("[AUX-DBF] Auxiliar creado en BD pero falló DBF: {}", e.getMessage());
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
    
            // 5. Sincronizar DBF
            try {
                String entidadCode = "";
                String unidadCode = "";
                if (ofDestino.getPredio() != null) {
                    unidadCode = ofDestino.getPredio().getUnidad() != null ? ofDestino.getPredio().getUnidad() : "";
                    if (ofDestino.getPredio().getEntidad() != null) {
                        entidadCode = ofDestino.getPredio().getEntidad().getEntidadCodigo() != null 
                                      ? ofDestino.getPredio().getEntidad().getEntidadCodigo() : "";
                    }
                }
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
            String prefijo        = config.getPrefijoDocumento();
            String codigoCompleto = prefijo + " " + nroDoc; // "Prev. 1234"

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

            for (Activo a : activos) {
                if (!a.getDescripcion().startsWith(codigoCompleto)) {
                    String nueva = codigoCompleto + " " + a.getDescripcion();
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
                asignacion = new AsignacionActivo();
                asignacion.setFechaAsignacion(LocalDateTime.now());
                asignacion.setResponsable(activos.get(0).getResponsable());
                asignacion = asignacionActivoService.save(asignacion);

                for (Activo a : activos) {
                    DetalleAsignacionActivo det = new DetalleAsignacionActivo();
                    det.setAsignacionActivo(asignacion);
                    det.setActivo(a);
                    det.setCodigoActivoSnapshot(a.getCodigo());
                    detalleAsignacionActivoService.save(det);
                }
            }

            asignacion.setCodigoDocumento(nroDoc);
            asignacion.setCodigoCompleto(codigoCompleto);
            asignacionActivoService.save(asignacion);

            String idEnc = Encriptar.encrypt(String.valueOf(asignacion.getIdAsignacionActivo()));

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
        } catch (Exception e) {
            log.error("Error editando activo pendiente", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
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
        return ResponseEntity.ok(resp);
    }

    private void aplicarCambios(Activo a, EditarActivoPendienteRequest req) {

        if (req.getDescripcion() != null && !req.getDescripcion().isBlank()) {
            String desc = req.getDescripcion().trim();
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
            a.setGrupoContable(grupoContableService.findById(req.getIdGrupoContable()));

        if (req.getIdOficina() != null)
            a.setOficina(oficinaService.findById(req.getIdOficina()));

        if (req.getIdResponsable() != null)
            a.setResponsable(responsableService.findById(req.getIdResponsable()));

        if (req.getIdAuxiliar() != null) {
            a.setAuxiliar(auxiliarService.findById(req.getIdAuxiliar()));
        } else {
            a.setAuxiliar(null);
        }

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

    /* =============================== */
    /* ========== PENDIENTES ========= */
    /* =============================== */

    @ValidarUsuarioAutenticado
    @GetMapping("/vistap")
    public String vista_activo_pendiente() {
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
                items.add(item);
            }

            dto.setItems(items);
            dto.setTotalActivos(asig.getDetalles().size());
            dto.setTotalPendientes(items.size());
            dto.setTotalSincronizados(
                asig.getDetalles().stream()
                    .filter(d -> "ACTIVO".equalsIgnoreCase(d.getActivo().getEstado()))
                    .count());
            dtos.add(dto);
        }

        List<Activo> sinAsignacion = activoService.listarActivosPendientes();
        List<String> sinAsignacionIds = new ArrayList<>();
        for (Activo a : sinAsignacion) {
            sinAsignacionIds.add(Encriptar.encrypt(String.valueOf(a.getIdActivo())));
        }

        model.addAttribute("asignaciones", dtos);
        model.addAttribute("sinAsignacion", sinAsignacion);
        model.addAttribute("sinAsignacionIds", sinAsignacionIds);
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

            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            String usuarioNombre = (usuario != null) ? usuario.getUsuario() : "SISTEMA";

            if (a.getOficina() == null || a.getOficina().getPredio() == null) {
                return Map.of("ok", false, "message", "Faltan datos de Oficina/Predio para sincronizar.");
            }

            String entidadCode = a.getOficina().getPredio().getEntidad().getEntidadCodigo();
            String unidadCode = a.getOficina().getPredio().getUnidad();

           if (actualDbfWriterService.existsByCodigo(a.getCodigo())) {
                log.warn("El activo {} ya existe en DBF. Se asume sincronizado.", a.getCodigo());
            } else {
                actualDbfWriterService.insertarDesdeActivo(a, entidadCode, unidadCode, usuarioNombre);
            }

            a.setEstado("ACTIVO");
            a.setApiEstado(Short.valueOf("1"));
            activoService.save(a);

            return Map.of("ok", true, "id", id, "message", "Activo aprobado y sincronizado.");
            
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
                    errores++; continue;
                }
                if (a.getOficina() == null || a.getOficina().getPredio() == null) {
                    errores++; detallesError.add("Activo " + a.getCodigo() + ": Faltan datos de Oficina.");
                    continue;
                }

                String entidadCode = a.getOficina().getPredio().getEntidad().getEntidadCodigo();
                String unidadCode = a.getOficina().getPredio().getUnidad();

                if (!actualDbfWriterService.existsByCodigo(a.getCodigo())) {
                    actualDbfWriterService.insertarDesdeActivo(a, entidadCode, unidadCode, usuarioNombre);
                }

                a.setEstado("ACTIVO");
                a.setApiEstado(Short.valueOf("1"));
                activoService.save(a);
                
                exitos++;

            } catch (Exception e) {
                log.error("Error en masivo id {}: {}", idEnc, e.getMessage());
                errores++;
                detallesError.add("Error desconocido en un activo.");
            }
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("ok", true);
        resp.put("exitos", exitos);
        resp.put("errores", errores);
        resp.put("msg", String.format("Proceso finalizado. Éxitos: %d | Errores: %d", exitos, errores));
        
        return ResponseEntity.ok(resp);
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