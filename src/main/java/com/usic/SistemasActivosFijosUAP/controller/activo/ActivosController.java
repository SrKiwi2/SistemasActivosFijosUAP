package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IConfiguracionGestionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEntidadService;
import com.usic.SistemasActivosFijosUAP.model.IService.IEstadoActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoFormDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.DataTablesResponse;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Auxiliar;
import com.usic.SistemasActivosFijosUAP.model.entity.ConfiguracionGestion;
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;
import com.usic.SistemasActivosFijosUAP.model.service.SyncControlService;

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
    private final IConfiguracionGestionService configuracionGestionService;

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
        String codigoActualStr = activo.getCodigo(); // Ej: "01-02-03-00020"

        List<String> idsReporte = new ArrayList<>();
        List<Activo> activosGuardados = new ArrayList<>();

        try {
            for (int i = 0; i < cantidad; i++) {
                // 1. CLONAR OBJETO (Crear nueva instancia basada en el formulario)
                Activo nuevoActivo = new Activo();
                
                // Copiar datos simples
                nuevoActivo.setDescripcion(activo.getDescripcion());
                nuevoActivo.setCosto(activo.getCosto());
                nuevoActivo.setVidaUtil(activo.getVidaUtil());
                nuevoActivo.setFechaAdquisicion(activo.getFechaAdquisicion());
                nuevoActivo.setObserv(activo.getObserv());
                
                // Copiar Relaciones (JPA Entities)
                nuevoActivo.setGrupoContable(activo.getGrupoContable());
                nuevoActivo.setOficina(activo.getOficina());
                nuevoActivo.setResponsable(activo.getResponsable());
                nuevoActivo.setOrganismoFinanciero(activo.getOrganismoFinanciero());
                nuevoActivo.setAuxiliar(activo.getAuxiliar());
                nuevoActivo.setEstadoActivo(activo.getEstadoActivo()); // Asumimos que viene del form o se setea default abajo

                // 2. GENERAR CÓDIGO CORRELATIVO
                // El primero (i=0) usa el código del formulario. Los siguientes se calculan.
                String codigoParaEste = (i == 0) 
                    ? codigoActualStr 
                    : incrementarCodigoString(codigoActualStr, i);
                
                nuevoActivo.setCodigo(codigoParaEste);
                
                // 3. DATOS DE AUDITORÍA Y ESTADO
                nuevoActivo.setUsuario(usuario.getUsuario());
                nuevoActivo.setFecMod(LocalDate.now());
                nuevoActivo.setFechaUlt(LocalDate.now());
                
                // Lógica de negocio default
                nuevoActivo.setApiEstado(Short.valueOf("3"));
                nuevoActivo.setCostoAnterior(0.0);
                nuevoActivo.setDepreciacionAcum(0.0);
                nuevoActivo.setVidaUtilAnterior(0);
                
                // Org Fin Code textual para DBF
                if(nuevoActivo.getOrganismoFinanciero() != null) {
                     OrganismoFinanciero of = organismoFinancieroService.findById(nuevoActivo.getOrganismoFinanciero().getIdOrganismoFinanciero());
                     nuevoActivo.setOrganismoFinanciero(of); // Re-attach para seguridad
                     nuevoActivo.setOrgFinCode(of.getCodOf());
                }
                
                // Estado Inicial
                if (nuevoActivo.getEstadoActivo() == null) {
                    nuevoActivo.setEstadoActivo(estadoActivoService.findById(1L)); // Bueno por defecto
                }
                
                nuevoActivo.setEstado("PENDIENTE"); // Siempre pendiente primero

                // 4. GUARDAR
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

    /**
     * Helper para incrementar códigos tipo "01-04-15-00022" + 1 -> "01-04-15-00023"
     */
    private String incrementarCodigoString(String codigoBase, int incremento) {
        try {
            // Asumimos formato XX-XX-XX-NNNNN
            int lastDash = codigoBase.lastIndexOf('-');
            if (lastDash == -1) return codigoBase + "-" + incremento; // Fallback

            String prefix = codigoBase.substring(0, lastDash + 1); // "01-04-15-"
            String numberPart = codigoBase.substring(lastDash + 1); // "00022"
            
            long numero = Long.parseLong(numberPart);
            long nuevoNumero = numero + incremento;
            
            // Reconstruir con padding de ceros (mismo largo que el original)
            String formato = "%0" + numberPart.length() + "d";
            return prefix + String.format(formato, nuevoNumero);
            
        } catch (Exception e) {
            log.error("No se pudo incrementar código: " + codigoBase);
            return codigoBase + "-" + incremento; // Fallback de emergencia
        }
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
            

            // Actualizar relaciones
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

            EstadoActivo estadoActivo = estadoActivoService.findById(1L); //EN UN FURO IMPLEMENTAR PARA COLOCAR SI ESTA BUENO, REGULAR O MALO
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
                    // Retornamos OK true porque la BD ya se actualizó, pero avisamos del error
                    return ResponseEntity.ok(Map.of("ok", true, "msg", "Guardado en BD, pero falló DBF: " + e.getMessage()));
                }
            }

            return ResponseEntity.ok(Map.of("ok", true, "msg", "Modificación guardada (Estado: Pendiente)"));

        } catch (Exception e) {
            log.error("Error fatal modificando activo", e);
            return ResponseEntity.status(500).body(Map.of("ok", false, "msg", "Error interno: " + e.getMessage()));
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
            
            // Validar que esté ACTIVO para poder darle de baja
            if (!"ACTIVO".equalsIgnoreCase(activo.getEstado())) {
                return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Solo se puede dar de baja activos que estén en estado ACTIVO."));
            }

            activo.setApiEstado(Short.valueOf("1")); 
            
            // Auditoría
            activo.setFecMod(LocalDate.now());
            activo.setFechaUlt(LocalDate.now());
            activo.setUsuMod(usuarioNombre);
            activo.setModificacion(new Date());
            if (usuario != null) activo.setModificacionIdUsuario(usuario.getIdUsuario());

            activoService.save(activo);
            
            // 2. Actualizar DBF
            try {
                // Obtenemos códigos para buscar en DBF
                String codigo = activo.getCodigo();
                String entidadCode = activo.getOficina().getPredio().getEntidad().getEntidadCodigo();
                String unidadCode = activo.getOficina().getPredio().getUnidad();
                
                // Usamos el mismo método de actualización, ya que el objeto 'activo' ya tiene el apiEstado=2
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
            @RequestParam("ids") List<String> idsEnc,
            @RequestParam("idConfig") Long idConfig,
            @RequestParam("nroDoc") String nroDoc) {

        int actualizados = 0;
        
        try {
            // 1. Obtener la Configuración (Ej: "Prev.")
            ConfiguracionGestion config = configuracionGestionService.findById(idConfig);
            
            String prefijo = config.getPrefijoDocumento(); // "Prev."

            // 2. Iterar y Actualizar
            for (String enc : idsEnc) {
                Long id = Long.parseLong(Encriptar.decrypt(enc));
                Activo a = activoService.findById(id);

                if (a != null && "PENDIENTE".equalsIgnoreCase(a.getEstado())) {
                    
                    // 🟢 MODIFICAR DESCRIPCIÓN EN BD
                    // Formato: "Prev. 5243 DESCRIPCION ORIGINAL"
                    String etiqueta = prefijo + " " + nroDoc;
                    
                    // Evitar duplicar si ya se asignó antes
                    if (!a.getDescripcion().startsWith(etiqueta)) {
                        String nuevaDesc = etiqueta + " " + a.getDescripcion();
                        // Recortar si excede 1024 (límite de tu entidad Activo)
                        if (nuevaDesc.length() > 1024) nuevaDesc = nuevaDesc.substring(0, 1024);
                        
                        a.setDescripcion(nuevaDesc);
                        a.setFecMod(LocalDate.now());
                        
                        activoService.save(a); // Solo guarda en PostgreSQL
                        actualizados++;
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                "ok", true, 
                "msg", "Asignación correcta.",
                "idsParaReporte", idsEnc, // Devolvemos los mismos IDs
                "nroPreventivo", nroDoc   // Devolvemos el número nuevo
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
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

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros_pendientes")
    public String tabla_registro_pendiente(Model model) throws Exception {
        List<Activo> listaActivosPendientes = activoService.listarActivosPendientes();
        List<String> encryptedIds = new ArrayList<>();
        for (Activo oficinas : listaActivosPendientes) {
            String id_encryptado = Encriptar.encrypt(Long.toString(oficinas.getIdActivo()));
            encryptedIds.add(id_encryptado);
        }
        model.addAttribute("listaActivosPendientes", listaActivosPendientes);
        model.addAttribute("id_encryptado", encryptedIds);
        return "activo/tabla_registros_pendientes";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/api/detalle/{idEnc}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalle(@PathVariable String idEnc) {
        try {
            Long id = Long.valueOf(Encriptar.decrypt(idEnc));
            Activo activo = activoService.findById(id);

            if (activo == null) {
                return ResponseEntity.notFound().build();
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("idActivo", activo.getIdActivo());
            response.put("codigo", activo.getCodigo());
            response.put("codigoSec", activo.getCodigoSec());
            response.put("descripcion", activo.getDescripcion());
            response.put("costo", activo.getCosto());
            response.put("vidaUtil", activo.getVidaUtil());
            response.put("fechaAdquisicion", activo.getFechaAdquisicion());
            response.put("estado", activo.getEstado());

            if (activo.getGrupoContable() != null) {
                response.put("grupoContable", Map.of(
                        "idGrupoContable", activo.getGrupoContable().getIdGrupoContable(),
                        "nombre", activo.getGrupoContable().getNombre()));
            }

            if (activo.getOficina() != null) {
                response.put("oficina", Map.of(
                        "idOficina", activo.getOficina().getIdOficina(),
                        "nombre", activo.getOficina().getNombre()));
            }

            if (activo.getResponsable() != null) {
                response.put("responsable", Map.of(
                        "idResponsable", activo.getResponsable().getIdResponsable(),
                        "persona", Map.of(
                                "nombreCompleto", activo.getResponsable().getPersona().getNombreCompleto())));
            }

            if (activo.getOrganismoFinanciero() != null) {
                response.put("organismoFinanciero", Map.of(
                        "idOrganismoFinanciero", activo.getOrganismoFinanciero().getIdOrganismoFinanciero(),
                        "descripcion", activo.getOrganismoFinanciero().getDescripcion()));
            }

            if (activo.getAuxiliar() != null) {
                response.put("auxiliar", Map.of(
                        "idAuxiliar", activo.getAuxiliar().getIdAuxiliar(),
                        "nombre", activo.getAuxiliar().getNombre()));
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error obteniendo detalle: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "message", "Error al obtener detalle: " + e.getMessage()));
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
                // No lanzamos error, permitimos que se marque como ACTIVO en BD para arreglar inconsistencia
            } else {
                // Insertar (Si falla, lanzará RuntimeException y saltará al catch)
                actualDbfWriterService.insertarDesdeActivo(a, entidadCode, unidadCode, usuarioNombre);
            }

            // 2) Si DBF ok → marca ACTIVO en Postgres
            a.setEstado("ACTIVO");
            a.setApiEstado(Short.valueOf("1"));
            activoService.save(a);

            return Map.of("ok", true, "id", id, "message", "Activo aprobado y sincronizado.");
            
        } catch (Exception e) {
            log.error("Error aprobando activo: {}", e.getMessage(), e);
            // Al capturar la excepción aquí, la BD NO se actualizó a ACTIVO. Correcto.
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

                // Validaciones básicas (igual que en individual)
                if (a == null || !"PENDIENTE".equalsIgnoreCase(a.getEstado())) {
                    errores++; continue;
                }
                if (a.getOficina() == null || a.getOficina().getPredio() == null) {
                    errores++; detallesError.add("Activo " + a.getCodigo() + ": Faltan datos de Oficina.");
                    continue;
                }

                // Datos para DBF
                String entidadCode = a.getOficina().getPredio().getEntidad().getEntidadCodigo();
                String unidadCode = a.getOficina().getPredio().getUnidad();

                // 1. Intentar DBF (Idempotente)
                if (!actualDbfWriterService.existsByCodigo(a.getCodigo())) {
                    actualDbfWriterService.insertarDesdeActivo(a, entidadCode, unidadCode, usuarioNombre);
                }

                // 2. Actualizar BD
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
                ", orgFin=" + (a.getOrganismoFinanciero() != null));
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

        if (a.getOrganismoFinanciero() != null) {
            dto.setOrganismoFinancieroId(a.getOrganismoFinanciero().getIdOrganismoFinanciero());
            dto.setOrganismoFinancieroNombre(a.getOrganismoFinanciero().getSigla());
        }
        return dto;
    }
}