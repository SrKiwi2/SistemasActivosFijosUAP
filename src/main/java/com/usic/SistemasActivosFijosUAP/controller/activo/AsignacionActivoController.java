package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.entity.Predio;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
@RequestMapping("/administracion/asignar")
@RequiredArgsConstructor
public class AsignacionActivoController {

    private final IPredioServicio predioServicio;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/asignacionActivo")
    public String asignarActivo(Model model) {
        List<Predio> listarDePredio = predioServicio.listarPredios();
        model.addAttribute("predios", listarDePredio);
        return "activo/asignacionActivos";
    }
}
