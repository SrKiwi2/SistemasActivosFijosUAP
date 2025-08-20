package com.usic.SistemasActivosFijosUAP.controller.gestion;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IngresoActivoAjenoService;
import com.usic.SistemasActivosFijosUAP.model.entity.IngresoActivoAjeno;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/seguimiento-activo")
@RequiredArgsConstructor
public class SeguimientoController {

    private final IngresoActivoAjenoService ingresoActivoAjenoService;

    //ASIGNACION ACTIVOS NUEVOS


    //INGRESO ACTIVO AJENOS
    @ValidarUsuarioAutenticado
    @GetMapping("/vista_ingreso_ajeno")
    public String inicio_ingresos_ajenos() {
        return "ingresos-ajenos/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla-registros-ajeno")
    public String tabla_ingresos(Model model) {
        List<IngresoActivoAjeno> ingresos = ingresoActivoAjenoService.findAll();
        model.addAttribute("ingresos", ingresos);
        return "ingresos-ajenos/tabla_registro";
    }
}