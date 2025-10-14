package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.util.ArrayList;
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
import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
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
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;
import com.usic.SistemasActivosFijosUAP.model.repository.FuncionesActivoRepo;

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
            dto.setNombre(activo.getNombre());
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
                dto.setAcciones("<button class='btn btn-sm btn-primary' onclick=\"editar('" + idEncriptado
                        + "')\">Editar</button>" +
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
    public ResponseEntity<ActivoFormDTO> buscarPorCodigo(@RequestParam("codigo") String codigo) {
        return activoService.fetchFullByCodigo(codigo)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ActivoFormDTO toDto(Activo a) {
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
            dto.setOrganismoFinancieroNombre(a.getOrganismoFinanciero().getDescripcion());
        }
        return dto;
    }
}