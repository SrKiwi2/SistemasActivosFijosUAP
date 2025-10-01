package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAuxiliarService;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoConsultaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.AuxOption;
import com.usic.SistemasActivosFijosUAP.model.dto.OficinaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.RespOption;
import com.usic.SistemasActivosFijosUAP.model.dto.ResponsableDTO;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

@RestController
@RequestMapping("/api")
public class CatalogoRestController {
    
    private final IResponsableService responsableService;
    private final IOficinaService oficinaService;
    private final IActivoService activoService;
    private final IAuxiliarService auxiliarService;

    public CatalogoRestController(IResponsableService responsableService, IOficinaService oficinaService, IActivoService activoService, IAuxiliarService auxiliarService) {
        this.responsableService = responsableService;
        this.oficinaService = oficinaService;
        this.activoService = activoService;
        this.auxiliarService = auxiliarService;
    }

    @GetMapping("/responsables")
    public List<ResponsableDTO> listarResponsables() {
        return responsableService.listarResponsables()
                .stream()
                .map(r -> new ResponsableDTO(r.getIdResponsable(), r.getPersona().getNombre() + " " +  
                                             r.getPersona().getPaterno() + " " + 
                                             r.getPersona().getMaterno() + " - " + 
                                             r.getOficina().getNombre()))
                .toList();
    }

    @GetMapping("/oficinas")
    public List<OficinaDTO> listarOficinas() {
        return oficinaService.listarOficinas()
                .stream()
                .map(o -> new OficinaDTO(o.getIdOficina(), o.getNombre()))
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

    /* Para cargar de mejor manera los responsbales.. */
    @GetMapping("/buscar_responsable")
    public Map<String, Object> search(
            @RequestParam(name = "q", required = false, defaultValue = "") String q,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page
    ) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize = 20; // 20 resultados por página
        Page<RespOption> result = responsableService.search(q, PageRequest.of(pageIndex, pageSize));

        Map<String, Object> resp = new HashMap<>();
        resp.put("results", result.getContent()); // cada item ya tiene {id, text}
        resp.put("pagination", Map.of("more", result.hasNext()));
        return resp;
    }

    @GetMapping("/buscar_auxiliar")
    public Map<String, Object> search(
            @RequestParam Long grupoId,
            @RequestParam(name = "q",    defaultValue = "") String q,
            @RequestParam(name = "page", defaultValue = "1") int page
    ) {
        int pageIndex = Math.max(0, page - 1);
        int pageSize  = 20;

        Page<AuxOption> result = auxiliarService.searchByGrupo(grupoId, q, PageRequest.of(pageIndex, pageSize));

        Map<String, Object> resp = new HashMap<>();
        resp.put("results", result.getContent());                // [{id, text}]
        resp.put("pagination", Map.of("more", result.hasNext()));
        return resp;
    }
}