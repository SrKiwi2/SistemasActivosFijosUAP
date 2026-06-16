package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;
import com.usic.SistemasActivosFijosUAP.model.entity.Responsable;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/ingreso")
@RequiredArgsConstructor
public class CIngresoActivoAjenoController {

    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String ingresoActivo(Model model) {
        cargarFiltros(model);
        return "seguimiento/ingreso/vista";
    }

    // Vista dedicada del módulo: registro de ingreso (foto + nota) + seguimiento con galería.
    @ValidarUsuarioAutenticado
    @GetMapping("/modulo")
    public String moduloIngreso(Model model) {
        cargarFiltros(model);
        return "operaciones/ingreso/modulo";
    }

    private void cargarFiltros(Model model) {
        List<Oficina> oficinas = oficinaService.listarOficinas();
        List<Responsable> responsables = responsableService.listarResponsables();
        model.addAttribute("oficinas", oficinas);
        model.addAttribute("responsables", responsables);
    }
}
