package com.usic.SistemasActivosFijosUAP.controller.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.dto.OficinaDTO;
import com.usic.SistemasActivosFijosUAP.model.dto.ResponsableDTO;

@RestController
@RequestMapping("/api")
public class CatalogoRestController {
    
    private final IResponsableService responsableService;
    private final IOficinaService oficinaService;

    public CatalogoRestController(IResponsableService responsableService, IOficinaService oficinaService) {
        this.responsableService = responsableService;
        this.oficinaService = oficinaService;
    }

    @GetMapping("/responsables")
    public List<ResponsableDTO> listarResponsables() {
        return responsableService.listarResponsables()
                .stream()
                .map(r -> new ResponsableDTO(r.getIdResponsable(), r.getPersona().getNombre()))
                .toList();
    }

    @GetMapping("/oficinas")
    public List<OficinaDTO> listarOficinas() {
        return oficinaService.listarOficinas()
                .stream()
                .map(o -> new OficinaDTO(o.getIdOficina(), o.getNombre()))
                .toList();
    }
}
