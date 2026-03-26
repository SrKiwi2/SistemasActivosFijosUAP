package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.entity.Oficina;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/ingreso")
@RequiredArgsConstructor
public class IngresoActivoAjenoController {

    private final IOficinaService oficinaService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String ingresoActivo(Model model) {
        List<Oficina> oficina = oficinaService.listarOficinas();
        model.addAttribute("oficinas", oficina);
        return "activo/ingresoActivoAjeno";
    }
}
