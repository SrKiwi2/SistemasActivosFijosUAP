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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.config.Encriptar;
import com.usic.SistemasActivosFijosUAP.interoperabilidad.JavaDbfService;
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ActualDbf;
import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;
import com.usic.SistemasActivosFijosUAP.model.service.ActivoSyncService;
import com.usic.SistemasActivosFijosUAP.model.service.ActivoSyncTracker;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

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
    private final JavaDbfService dbfService;
    private final ActivoSyncTracker tracker;
    private final ActivoSyncService activoSyncService;


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
    @PostMapping("/formulario")
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
    @PostMapping("/registrar-activo")
    public ResponseEntity<String> registrar_activo(HttpServletRequest request, @Validated Activo activo) {
        if (activoService.buscarPorNombre(activo.getNombre()) == null) {
            activo.setEstado("ACTIVO");
            activoService.save(activo);
            return ResponseEntity.ok("Se realizó el registro correctamente");
        } else {
            return ResponseEntity.ok("Ya existe un rol con este nombre");
        }
    }

    @PostMapping(value = "/modificar-activo")
    public ResponseEntity<String> modificar_oficina(HttpServletRequest request, Activo activo,
            RedirectAttributes redirectAttrs) {
        Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
        activo.setModificacionIdUsuario(usuario.getIdUsuario());
        activo.setEstado("ACTIVO");
        activoService.save(activo);
        return ResponseEntity.ok("Se realizó el registro correctamente");
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
    public Map<String,Object> listarActivosDatatables(@RequestParam Map<String, String> params) throws Exception {
        int start = Integer.parseInt(params.getOrDefault("start","0"));
        int length = Integer.parseInt(params.getOrDefault("length","10"));
        String searchValue = params.get("search[value]");

        String codigo = params.get("codigo");
        String responsableId = params.get("responsable");
        String oficinaId = params.get("oficina");
        String fecha = params.get("fecha");

        PageRequest pageRequest = PageRequest.of(Math.max(start,0) / Math.max(length,1), Math.max(length,1));

        // 1) Primero intenta BD con tus filtros
        Page<Activo> pagina = activoService.buscarConFiltros(
                searchValue, codigo, responsableId, oficinaId, fecha, pageRequest);

        List<ActivoDTO> filas = new ArrayList<>();
        long total = 0;
        String source = "db";

        if (pagina != null && !pagina.isEmpty()) {
            total = pagina.getTotalElements();
            for (Activo a : pagina.getContent()) {
                ActivoDTO dto = new ActivoDTO();
                dto.setIndex(""); // lo pinta DataTables
                dto.setCodigo(nvl(a.getCodigo()));
                dto.setNombre(nvl(a.getNombre()));
                dto.setDescripcion(nvl(a.getDescripcion()));
                dto.setResponsable(a.getResponsable()==null || a.getResponsable().getPersona()==null
                        ? "" 
                        : nvl(a.getResponsable().getPersona().getNombre()) + " " +
                        nvl(a.getResponsable().getPersona().getPaterno()) + " " +
                        nvl(a.getResponsable().getPersona().getMaterno()));
                dto.setOficina(a.getOficina()==null ? "" : nvl(a.getOficina().getNombre()));
                dto.setCosto(a.getCosto());
                dto.setVidaUtil(a.getVidaUtil());
                dto.setFechaAdquisicion(a.getFechaAdquisicion()==null ? "" : a.getFechaAdquisicion().toString());
                dto.setEstado(a.getEstadoActivo()==null ? "" : nvl(a.getEstadoActivo().getNombre()));
                try {
                    String idEnc = Encriptar.encrypt(String.valueOf(a.getIdActivo()));
                    dto.setIdEnc(idEnc);
                    dto.setAcciones(
                        "<button class='btn btn-sm btn-primary' onclick=\"editar('" + idEnc + "')\">Editar</button> " +
                        "<button class='btn btn-sm btn-danger' onclick=\"eliminar('" + escapeHtml(nvl(a.getNombre())) + "','" + idEnc + "')\">Eliminar</button>"
                    );
                } catch (Exception e) {
                    dto.setIdEnc("");
                    dto.setAcciones("<span class='text-danger'>Error acciones</span>");
                }
                dto.setSource("db");
                filas.add(dto);
            }
        } else {
            // 2) Fallback: leer del DBF montado (preview SOLO lectura)
            source = "dbf";

            // Puedes tomar como “q” lo más relevante: prioridad al filtro por código,
            // si no, usa el buscador general de DataTables:
            String q = (codigo != null && !codigo.isBlank())
                    ? codigo
                    : (searchValue == null || searchValue.isBlank() ? null : searchValue);

            var todos = dbfService.listarActualAll(q); // ya lo tienes implementado
            total = todos.size();

            // Paginación manual (start/length)
            int end = Math.min(start + length, todos.size());
            List<ActualDbf> page = (start >= end) ? List.of() : todos.subList(start, end);

            for (var f : page) {
                ActivoDTO dto = new ActivoDTO();
                dto.setIndex("");
                dto.setCodigo(nvl(f.getCodigo()));
                dto.setNombre(trunc(nvl(f.getDescripcion()), 255));
                dto.setDescripcion(nvl(f.getDescripcion()));

                // Responsable (del DBF solo tenemos codResp; mostramos como etiqueta)
                dto.setResponsable(isBlank(f.getCodRespTxt()) ? "" : ("CODRESP " + f.getCodRespTxt()));

                // Oficina (del DBF: unidad + codoﬁ)
                String oficinaLabel = nvl(f.getUnidad());
                if (f.getCodOfi()!=null) oficinaLabel += " - OF." + f.getCodOfi();
                dto.setOficina(oficinaLabel);

                dto.setCosto(f.getCosto());
                dto.setVidaUtil(f.getVidaUtil());
                dto.setFechaAdquisicion(formatYMD(f.getAno(), f.getMes(), f.getDia())); // helper abajo

                dto.setEstado(f.getCodEstado()==null ? "" : ("EST-" + f.getCodEstado()));
                dto.setIdEnc(""); // sin ID real en modo DBF
                dto.setAcciones("<span class='text-muted'>Vista previa</span>");
                dto.setSource("dbf");
                filas.add(dto);
            }
        }

        // respuesta DataTables
        Map<String,Object> res = new LinkedHashMap<>();
        res.put("draw", Integer.parseInt(params.getOrDefault("draw","1")));
        res.put("recordsTotal", total);
        res.put("recordsFiltered", total);
        res.put("data", filas);
        res.put("source", source); // para mostrar un badge en el front
        return res;
    }

    /*
     * para consultar codigo codigo correlavtio segun municpio - predio -
     * grupocontable
     */
    @PostMapping(value = "/generar-correlativo", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> generar(
            @RequestParam String mun,
            @RequestParam String pred,
            @RequestParam String grp) {
        String codigo = funciones.previewCodigoPorCodes(mun, pred, grp);
        return Map.of("codigo", codigo);
    }

    @GetMapping("/sync-progress")
    @ResponseBody
    public Map<String,Object> syncProgress() {
        return tracker.snapshot();
    }


    @PostMapping("/start-sync")
    @ResponseBody
    public ResponseEntity<?> startSync(
        @RequestParam(name="q", required=false) String q,
        @RequestParam(name="gestion", required=false) Short gestionPreferida) {

        activoSyncService.startSync(q, gestionPreferida);
        return ResponseEntity.accepted().body(Map.of("ok", true));
    }

    private static String escapeHtml(String s){ return s==null? "": s.replace("'", "\\'").replace("\"","&quot;"); }

    private static String formatYMD(Integer y, Integer m, Integer d){
        if (y==null) return "";
        String mm = (m==null? "01" : String.format("%02d", m));
        String dd = (d==null? "01" : String.format("%02d", d));
        return y + "-" + mm + "-" + dd;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String s) {
        return (s == null) ? "" : s;
    }

    private LocalDate buildDate(Integer y, Integer m, Integer d) {
        try {
            if (y == null || m == null || d == null || y <= 0 || m <= 0 || d <= 0) return null;
            return LocalDate.of(y, m, d);
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean boolVal(Boolean b) { return (b == null) ? null : b; }

    private String trunc(String s, int n) {
        if (s == null)
            return null;
        return s.length() > n ? s.substring(0, n) : s;
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
        } catch (Exception e) {
            return s;
        }
    }

}