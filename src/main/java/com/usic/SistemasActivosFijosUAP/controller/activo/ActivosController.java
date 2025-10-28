package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.registroDbf.ActualDbfWriterService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
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
import com.usic.SistemasActivosFijosUAP.model.entity.Entidad;
import com.usic.SistemasActivosFijosUAP.model.entity.EstadoActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.OrganismoFinanciero;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;

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
    private final IEntidadService entidadService;
    private final IMunicipioService municipioService;
    private final IPredioServicio predioServicio;
    private final IGrupoContableService grupoContableService;
    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;
    private final IOrganismoFinancieroService organismoFinancieroService;
    private final IAuxiliarService auxiliarService;
    private final IEstadoActivoService estadoActivoService;
    private final ActualDbfWriterService actualDbfWriterService;

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
    @PostMapping(value="/registrar-activo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> registrar_activo(
            HttpServletRequest request,
            @Validated @ModelAttribute Activo activo,
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

        // LOG de lo recibido (parcial para no cargar mucho)
        log.info("Recibido Activo => codigo={}, fechaAdq={}, descripcion={}, costo={}, vidaUtil={}, " +
                        "grupoContableId={}, oficinaId={}, responsableId={}, orgFinId={}, auxiliarId={}",
                activo.getCodigo(),
                activo.getFechaAdquisicion(),
                activo.getDescripcion(),
                activo.getCosto(),
                activo.getVidaUtil(),
                activo.getGrupoContable() != null ? activo.getGrupoContable().getIdGrupoContable() : null,
                activo.getOficina() != null ? activo.getOficina().getIdOficina() : null,
                activo.getResponsable() != null ? activo.getResponsable().getIdResponsable() : null,
                activo.getOrganismoFinanciero() != null ? activo.getOrganismoFinanciero().getIdOrganismoFinanciero() : null,
                activo.getAuxiliar() != null ? activo.getAuxiliar().getIdAuxiliar() : null
        );

        // (opcional) lógica de negocio mínima
        activo.setApiEstado(Short.valueOf("3"));    
        activo.setCostoAnterior(0.0);
        activo.setDepreciacionAcum(0.0);

        activo.setVidaUtilAnterior(0);
        EstadoActivo estadoActivo = estadoActivoService.findById(1L);
        activo.setEstadoActivo(estadoActivo);
        
        OrganismoFinanciero organismoFinanciero = organismoFinancieroService.findById(activo.getOrganismoFinanciero().getIdOrganismoFinanciero());
        activo.setOrganismoFinanciero(organismoFinanciero);

        activo.setOrgFinCode(activo.getOrganismoFinanciero().getCodOf());
        activo.setUsuario(usuario.getUsuario());
        activo.setFecMod(LocalDate.now());
        activo.setFechaUlt(activo.getFecMod());

        activo.setEstado("PENDIENTE");
        activoService.save(activo);

        // ECO JSON (útil para probar rápido en el front)
        Map<String, Object> activoMap = new LinkedHashMap<>();
        activoMap.put("id", activo.getIdActivo());
        activoMap.put("codigo", activo.getCodigo());
        activoMap.put("fechaAdquisicion", activo.getFechaAdquisicion());
        activoMap.put("descripcion", activo.getDescripcion());
        activoMap.put("costo", activo.getCosto());
        activoMap.put("vidaUtil", activo.getVidaUtil());
        activoMap.put("grupoContableId", activo.getGrupoContable() != null ? activo.getGrupoContable().getIdGrupoContable() : null);
        activoMap.put("oficinaId", activo.getOficina() != null ? activo.getOficina().getIdOficina() : null);
        activoMap.put("responsableId", activo.getResponsable() != null ? activo.getResponsable().getIdResponsable() : null);
        activoMap.put("orgFinId", activo.getOrganismoFinanciero() != null ? activo.getOrganismoFinanciero().getIdOrganismoFinanciero() : null);
        activoMap.put("auxiliarId", activo.getAuxiliar() != null ? activo.getAuxiliar().getIdAuxiliar() : null);

        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("ok", true);
        ok.put("msg", "Se realizó el registro correctamente");
        ok.put("activo", activoMap);

        return ResponseEntity.ok(ok);
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
                        .toList()
            ));
        }
        
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        String usuarioNombre = usuario.getUsuario();
        
        // Obtener el activo original de la BD
        Activo activoOriginal = activoService.findById(activoForm.getIdActivo());
        if (activoOriginal == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "ok", false,
                "msg", "No se encontró el activo con ID: " + activoForm.getIdActivo()
            ));
        }
        
        // Guardar el código original para buscar en DBF
        String codigoOriginal = activoOriginal.getCodigo();
        
        // Actualizar campos del activo
        activoOriginal.setCodigo(activoForm.getCodigo());
        activoOriginal.setCodigoSec(activoForm.getCodigoSec());
        activoOriginal.setDescripcion(activoForm.getDescripcion());
        activoOriginal.setCosto(activoForm.getCosto());
        activoOriginal.setVidaUtil(activoForm.getVidaUtil());
        activoOriginal.setFechaAdquisicion(activoForm.getFechaAdquisicion());
        
        // Actualizar relaciones
        if (activoForm.getGrupoContable() != null) {
            activoOriginal.setGrupoContable(activoForm.getGrupoContable());
        }
        Oficina oficinaForm = activoForm.getOficina();
        if (oficinaForm != null && oficinaForm.getIdOficina() != null) {
            // Asumiendo que tiene un OficinaService inyectado
            Oficina oficinaCompleta = oficinaService.findById(oficinaForm.getIdOficina());
            activoOriginal.setOficina(oficinaCompleta);
        } else {
            activoOriginal.setOficina(null);
        }
        if (activoForm.getResponsable() != null) {
            activoOriginal.setResponsable(activoForm.getResponsable());
        }
       if (activoForm.getOrganismoFinanciero() != null && activoForm.getOrganismoFinanciero().getIdOrganismoFinanciero() != null) {
            OrganismoFinanciero orgFin = organismoFinancieroService.findById(
                activoForm.getOrganismoFinanciero().getIdOrganismoFinanciero()
            );
            activoOriginal.setOrganismoFinanciero(orgFin);
            // Si el organismo fue encontrado, se setea el código
            if (orgFin != null) {
                activoOriginal.setOrgFinCode(orgFin.getCodOf());
            }
        } else {
            activoOriginal.setOrganismoFinanciero(null);
            activoOriginal.setOrgFinCode(null);
        }
        if (activoForm.getAuxiliar() != null) {
            activoOriginal.setAuxiliar(activoForm.getAuxiliar());
        }
        
        // Actualizar metadatos de modificación
        activoOriginal.setFecMod(LocalDate.now());
        activoOriginal.setUsuMod(usuarioNombre);
        activoOriginal.setEstado("ACTIVO");
        activoOriginal.setModificacionIdUsuario(usuario.getIdUsuario());
        activoOriginal.setUsuario(usuarioNombre);
        activoOriginal.setEstadoActivo(activoForm.getEstadoActivo());
        activoOriginal.setVidaUtilAnterior(0);
        activoOriginal.setFechaUlt(LocalDate.now());
        activoOriginal.setFecMod(LocalDate.now());
        activoOriginal.setCostoAnterior(Double.valueOf(0));
        activoOriginal.setApiEstado(Short.valueOf("3"));

        // 1) Guardar en PostgreSQL
        activoService.save(activoOriginal);
        
        // 2) Actualizar en DBF (solo si está ACTIVO)
        if ("ACTIVO".equalsIgnoreCase(activoOriginal.getEstado())) {
            
            Oficina oficina = activoOriginal.getOficina();
            Predio predio = (oficina != null) ? oficina.getPredio() : null;
            Entidad entidad = (predio != null) ? predio.getEntidad() : null;

            if (oficina == null || predio == null || entidad == null) {
                log.error("Activo {} tiene datos incompletos (Oficina/Predio/Entidad) para DBF. Se omitió la sincronización.", activoOriginal.getCodigo());
                
                // Retorna un error 400 ya que faltan datos esenciales para el proceso
                return ResponseEntity.status(400).body(Map.of(
                    "ok", true, // Lo ponemos en true, pero con advertencia, si el guardado en PG fue exitoso
                    "msg", "Se modificó en PostgreSQL, pero FALTAN datos de relación (Oficina/Predio/Entidad) para actualizar en DBF."
                ));
            }

            try {
                String entidadCode = entidad.getEntidadCodigo();
                String unidadCode = predio.getUnidad();
                
                actualDbfWriterService.actualizarDesdeActivo(
                    codigoOriginal, 
                    activoOriginal, 
                    entidadCode, 
                    unidadCode, 
                    usuarioNombre
                );
                
                log.info("Activo {} actualizado en PostgreSQL y DBF", activoOriginal.getCodigo());
                
            } catch (Exception e) {
                log.error("Error actualizando DBF para activo {}: {}", 
                        activoOriginal.getCodigo(), e.getMessage(), e);
                
                return ResponseEntity.status(500).body(Map.of(
                    "ok", false,
                    "msg", "Se guardó en la base de datos pero falló la actualización en DBF: " + e.getMessage()
                ));
            }
        }
        
        return ResponseEntity.ok(Map.of(
            "ok", true, 
            "msg", "Se modificó correctamente en PostgreSQL" + 
                ("ACTIVO".equalsIgnoreCase(activoOriginal.getEstado()) ? " y DBF" : "")
        ));
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
                    "nombre", activo.getGrupoContable().getNombre()
                ));
            }
            
            if (activo.getOficina() != null) {
                response.put("oficina", Map.of(
                    "idOficina", activo.getOficina().getIdOficina(),
                    "nombre", activo.getOficina().getNombre()
                ));
            }
            
            if (activo.getResponsable() != null) {
                response.put("responsable", Map.of(
                    "idResponsable", activo.getResponsable().getIdResponsable(),
                    "persona", Map.of(
                        "nombreCompleto", activo.getResponsable().getPersona().getNombreCompleto()
                    )
                ));
            }
            
            if (activo.getOrganismoFinanciero() != null) {
                response.put("organismoFinanciero", Map.of(
                    "idOrganismoFinanciero", activo.getOrganismoFinanciero().getIdOrganismoFinanciero(),
                    "descripcion", activo.getOrganismoFinanciero().getDescripcion()
                ));
            }
            
            if (activo.getAuxiliar() != null) {
                response.put("auxiliar", Map.of(
                    "idAuxiliar", activo.getAuxiliar().getIdAuxiliar(),
                    "nombre", activo.getAuxiliar().getNombre()
                ));
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error obteniendo detalle: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "ok", false,
                "message", "Error al obtener detalle: " + e.getMessage()
            ));
        }
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/api/aprobar/{idEnc}")
    @ResponseBody
    public Map<String, Object> aprobarActivo(@PathVariable String idEnc,
                                            HttpServletRequest request) throws Exception {
        Long id = Long.valueOf(Encriptar.decrypt(idEnc));
        Activo a = activoService.findById(id);

        Usuario usuarios = (Usuario) request.getSession().getAttribute("usuario");
        if (!"PENDIENTE".equalsIgnoreCase(a.getEstado())) {
            return Map.of("ok", false, "message", "El activo no está en estado PENDIENTE.");
        }

        // ====== prepara códigos mínimos requeridos por DBF ======
        // ⚠️ AJUSTA: cómo obtienes ENTIDAD y UNIDAD para ACTUAL.DBF
        Entidad entidad = a.getOficina().getPredio().getEntidad();
        String entidadCode = entidad.getEntidadCodigo(); // o desde configuración (ApplicationProperties)
        Predio predio = a.getOficina().getPredio();
        String unidadCode  = predio.getUnidad(); // fallback
        String usuario = usuarios.getUsuario();

        // 1) Insertar en ACTUAL.DBF (si no existe ya por CODIGO)
        if (actualDbfWriterService.existsByCodigo(a.getCodigo())) {
            // Si ya existe, puedes decidir: error, o continuar (idempotente)
            // Aquí continuamos como idempotente:
        } else {
            try {
                actualDbfWriterService.insertarDesdeActivo(a, entidadCode, unidadCode, usuario);
            } catch (Exception e) {
                // Loguea y devuelve error: NO cambiamos estado en Postgres
                e.printStackTrace();
                return Map.of("ok", false, "message", "No se pudo registrar en ACTUAL.DBF: " + e.getMessage());
            }
        }

        // 2) Si DBF ok → marca ACTIVO en Postgres
        a.setEstado("ACTIVO");
        activoService.save(a);

        return Map.of("ok", true, "id", id);
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
        System.out.println("oficina=" + (a.getOficina()!=null) +
                     ", predio=" + (a.getOficina()!=null && a.getOficina().getPredio()!=null) +
                     ", municipio=" + (a.getOficina()!=null && a.getOficina().getPredio()!=null && a.getOficina().getPredio().getMunicipio()!=null) +
                     ", grupo=" + (a.getGrupoContable()!=null) +
                     ", aux=" + (a.getAuxiliar()!=null) +
                     ", resp=" + (a.getResponsable()!=null) +
                     ", persona=" + (a.getResponsable()!=null && a.getResponsable().getPersona()!=null) +
                     ", orgFin=" + (a.getOrganismoFinanciero()!=null));
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