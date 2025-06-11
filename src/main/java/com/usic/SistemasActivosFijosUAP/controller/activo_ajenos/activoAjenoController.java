package com.usic.SistemasActivosFijosUAP.controller.activo_ajenos;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.model.IService.IngresoActivoAjenoService;
import com.usic.SistemasActivosFijosUAP.model.entity.IngresoActivoAjeno;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/reporte")
@RequiredArgsConstructor
public class activoAjenoController {

    private final IngresoActivoAjenoService ingresoActivoAjenoService;

    @GetMapping("/ver-ingresos")
    public String verIngresos(Model model) {
        List<IngresoActivoAjeno> ingresos = ingresoActivoAjenoService.findAll();
        model.addAttribute("ingresos", ingresos);
        return "ingresos-ajenos/lista";
    }

}
