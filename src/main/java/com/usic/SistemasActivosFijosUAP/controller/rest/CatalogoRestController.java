package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.ICargoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IGrupoContableService;
import com.usic.SistemasActivosFijosUAP.model.IService.IMunicipioService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOrganismoFinancieroService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPersonaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoConsultaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.AuxOption;
import com.usic.SistemasActivosFijosUAP.model.dto.GrupoMetaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.OficinaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.RespOption;
import com.usic.SistemasActivosFijosUAP.model.dto.ResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Cargo;
import com.usic.SistemasActivosFijosUAP.model.entity.GrupoContable;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Persona;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;
import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api")
public class CatalogoRestController {
    
    private final IResponsableService responsableService;
    private final IOficinaService oficinaService;
    private final IActivoService activoService;
    private final IAuxiliarService auxiliarService;
    private final IGrupoContableService grupoContableService;
    private final IPredioServicio predioServicio;
    private final IPersonaService personaService;
    private final IMunicipioService municipioService;
    private final IOrganismoFinancieroService organismoFinancieroService;
    private final ICargoService cargoService;

    public CatalogoRestController(IResponsableService responsableService, 
        IOficinaService oficinaService, 
        IActivoService activoService, 
        IAuxiliarService auxiliarService, 
        IGrupoContableService grupoContableService, 
        IPersonaService personaService,
        IPredioServicio predioServicio,
        IMunicipioService municipioService,
        IOrganismoFinancieroService organismoFinancieroService,
        ICargoService cargoService) {
        this.responsableService = responsableService;
        this.oficinaService = oficinaService;
        this.activoService = activoService;
        this.personaService = personaService;
        this.auxiliarService = auxiliarService;
        this.grupoContableService = grupoContableService;
        this.predioServicio = predioServicio;
        this.municipioService = municipioService;
        this.organismoFinancieroService = organismoFinancieroService;
        this.cargoService = cargoService;
    }

    @GetMapping("/responsables")
    @Transactional(readOnly = true)
    public List<ResponsableDTO> listarResponsables() {
        return responsableService.listarResponsables()
                .stream()
                .map(r -> new ResponsableDTO(r.getIdResponsable(), r.getPersona().getNombre() + " " +  
                                             r.getPersona().getPaterno() + " " + 
                                             r.getPersona().getMaterno() + " - " + 
                                             r.getOficina().getCodOfi()+ " - " +
                                             r.getOficina().getPredio().getUnidad()))
                .toList();
    }

    @GetMapping("/oficinas")
    public List<OficinaDTO> listarOficinas() {
        return oficinaService.listarOficinas()
                .stream()
                .map(o -> {
            OficinaDTO dto = new OficinaDTO(o.getIdOficina(), o.getNombre());
            dto.setCodigo(o.getCodOfi());
            return dto;
        })
        .toList();
    }

    @GetMapping("/oficinas/sugerencias")
    @ResponseBody
    public List<String> sugerenciasOficinas(@RequestParam String termino) {
        return oficinaService.buscarPorNombreParcial(termino)
                            .stream()
                            .map(Oficina::getNombre)
                            .collect(Collectors.toList());
    }

    @GetMapping("/responsables/datos")
    @ResponseBody
    public Map<String, String> obtenerDatosResponsable(@RequestParam String codigo) {
        Responsable responsable = responsableService.buscarPorCodigo(codigo);
        Map<String, String> datos = new HashMap<>();
        if (responsable != null && responsable.getPersona() != null) {
            datos.put("ci", responsable.getPersona().getCi());
            datos.put("nombreCompleto", responsable.getPersona().getNombreCompleto());
        }
        return datos;
    }

    @GetMapping("/buscar-activo")
    public ResponseEntity<ActivoConsultaDTO> obtenerPorCodigo(@RequestParam String codigo) {
        return activoService.findByCodigo(codigo)
            .map(a -> {
                String oficinaTexto = null;
                Long oficinaId = null;
                String oficinaNombre = null;

                if (a.getOficina() != null) {
                    oficinaId = a.getOficina().getIdOficina();
                    oficinaNombre = a.getOficina().getNombre();
                }

                return ResponseEntity.ok(
                    new ActivoConsultaDTO(
                        a.getCodigo(),
                        a.getDescripcion(),
                        oficinaId,
                        oficinaNombre,
                        (oficinaTexto == null || oficinaTexto.isBlank()) ? oficinaNombre : oficinaTexto
                    )
                );
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/buscar-activo-responsable")
    public ResponseEntity<ActivoResponsableDTO> obtenerConResponsable(@RequestParam String codigo) {
        return activoService.findByCodigo(codigo)
            .map(a -> {
                String oficinaNombre = a.getOficina() != null ? a.getOficina().getNombre() : null;
                String responsableNombre = a.getResponsable() != null
                        ? a.getResponsable().getPersona().getNombreCompleto()
                        : "Sin responsable asignado";

                return ResponseEntity.ok(new ActivoResponsableDTO(
                        a.getCodigo(),
                        a.getDescripcion(),
                        a.getOficina() != null ? a.getOficina().getIdOficina() : null,
                        oficinaNombre,
                        responsableNombre,
                        a.getResponsable() != null ? a.getResponsable().getIdResponsable() : null
                ));
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/meta")
    public ResponseEntity<GrupoMetaDTO> meta(@PathVariable Long id) {
        GrupoContable g = grupoContableService.findById(id);
        if (g == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new GrupoMetaDTO(
                g.getVidaUtil(),
                g.getDepreciar(),
                g.getActualizar()
        ));
    }

    @GetMapping("/buscar_responsable")
    public Map<String, Object> search(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "oficinaId", required = false) Long oficinaId
    ) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize = 20;
        PageRequest pageRequest = PageRequest.of(pageIndex, pageSize);
        
        Page<RespOption> result;

        if (oficinaId != null && oficinaId > 0) {
            result = responsableService.searchByOficina(oficinaId, q, pageRequest);
        } else if (oficinaId != null && oficinaId == -1) {
             result = Page.empty();
        } else {
            result = responsableService.searchGlobal(q, pageRequest); 
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("results", result.getContent());
        resp.put("pagination", Map.of("more", result.hasNext()));
        
        return resp;
    }

    @PostMapping("/api/responsable/registrar-rapido")
    @ResponseBody
    public ResponseEntity<?> registrarRapido(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        try {
            Long idOficina = Long.valueOf(body.get("idOficina"));
            Oficina oficina = oficinaService.findById(idOficina);
            if (oficina == null) {
                return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "msg", "Oficina no encontrada"));
            }

            String ci = body.get("ci");
            Persona persona = personaService.findByCi(ci)
            .orElseGet(() -> {
                Persona p = new Persona();
                p.setNombre(body.get("nombre"));
                p.setPaterno(body.get("paterno"));
                p.setMaterno(body.getOrDefault("materno", null));
                p.setCi(ci);
                return personaService.save(p);
            });

            boolean yaExiste = responsableService
                .existsByOficinaIdOficinaAndPersonaIdPersona(idOficina, persona.getIdPersona());
            if (yaExiste) {
                return ResponseEntity.badRequest().body(
                    Map.of("ok", false, "msg", "Ya existe un responsable con ese CI en esta oficina."));
            }

            Responsable resp = new Responsable();
            resp.setPersona(persona);
            resp.setOficina(oficina);
            resp.setCodigoFuncionario(body.getOrDefault("codigoFuncionario", null));
            resp.setApiEstado(Short.valueOf("1"));
            resp.setFechaUlt(LocalDate.now());

            Usuario usuario = (Usuario) request.getSession().getAttribute("usuario");
            resp.setUsuario(usuario != null ? usuario.getUsuario() : "SISTEMA");

            responsableService.save(resp);

            return ResponseEntity.ok(Map.of(
                "ok", true,
                "idResponsable", resp.getIdResponsable(),
                "nombreCompleto", persona.getNombreCompleto(),
                "msg", "Responsable registrado correctamente"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(500)
                .body(Map.of("ok", false, "msg", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/buscar_auxiliar")
    public Map<String, Object> search(
            @RequestParam Long grupoId,
            @RequestParam(required = false) Long predioId,  
            @RequestParam(name = "q",    defaultValue = "") String q,
            @RequestParam(name = "page", defaultValue = "1") int page
    ) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize  = 20;

        Page<AuxOption> result = auxiliarService.searchByGrupo(grupoId, predioId, q, PageRequest.of(pageIndex, pageSize));

        Map<String, Object> resp = new HashMap<>();
        resp.put("results", result.getContent());
        resp.put("pagination", Map.of("more", result.hasNext()));
        return resp;
    }

    @GetMapping("/buscar_predios")
    public List<Map<String, ?>> buscarPredios(@RequestParam Long municipioId) {
        return predioServicio.findByMunicipioIdMunicipio(municipioId).stream()
            .map(p -> Map.of(
                "id", p.getIdPredio(),
                "descrip", p.getDescrip() + " (" + p.getUnidad() + ")",
                "codigo", p.getCodigo()
            ))
            .collect(Collectors.toList());
    }

    @GetMapping("/buscar_oficinas")
    public List<Map<String, ?>> buscarOficinas(@RequestParam Long predioId) {
        return oficinaService.findByPredioIdPredio(predioId).stream()
            .map(o -> Map.of(
                "id", o.getIdOficina(),
                "nombre", o.getNombre() + " (" + o.getCodOfi() + ")",
                "codigo", o.getCodOfi()
            ))
            .collect(Collectors.toList());
    }

    @GetMapping("/buscar_responsables_lista")
    public List<Map<String, ?>> buscarResponsables(@RequestParam Long oficinaId) {
        return responsableService.findByOficinaIdOficina(oficinaId).stream()
            .map(r -> Map.of(
                "id", r.getIdResponsable(),
                "text", r.getPersona().getNombreCompleto()
            ))
            .collect(Collectors.toList());
    }

    @GetMapping("/buscar_auxiliar_lista")
    public List<Map<String, ?>> buscarAuxiliares(@RequestParam Long grupoId , @RequestParam Long predioId) {
        return auxiliarService.findByPredioIdPredioAndGrupoContableIdGrupoContable(predioId, grupoId).stream()
            .map(a -> Map.of(
                "id", a.getIdAuxiliar(),
                "text", a.getNombre()
            ))
            .collect(Collectors.toList());
    }

    /**
     * Municipios — para el primer select en cascada del modal de edición.
     * GET /api/municipios/listar
     */
    @GetMapping("/municipios/listar")
    public List<Map<String, Object>> listarMunicipios() {
        return municipioService.findAll().stream()
            .map(m -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("idMunicipio", m.getIdMunicipio());
                r.put("nombre",      m.getNombre());
                r.put("codigo",      m.getCodigo());   // usado como data-code en el select
                return r;
            })
            .toList();
    }

    /**
     * Grupos contables — para el select de clasificación.
     * El JS lo llama con fetch('/api/grupos/listar').
     * GET /api/grupos/listar
     */
    @GetMapping("/grupos/listar")
    public List<Map<String, Object>> listarGrupos() {
        return grupoContableService.findAll().stream()
            .map(g -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("idGrupoContable", g.getIdGrupoContable());
                r.put("nombre",          g.getNombre());
                r.put("codDbf",          g.getCodDbf());   // usado como data-code para preview código
                return r;
            })
            .toList();
    }

    /**
     * Organismos financieros — para el select de financiamiento (opcional).
     * GET /api/organismos/listar
     */
    @GetMapping("/organismos/listar")
    public List<Map<String, Object>> listarOrganismos() {
        return organismoFinancieroService.findAll().stream()
            .map(o -> {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("idOrganismoFinanciero", o.getIdOrganismoFinanciero());
                r.put("sigla",                 o.getSigla());
                r.put("descripcion",           o.getDescripcion());
                return r;
            })
            .toList();
    }

    @GetMapping("/cargos/search")
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
}