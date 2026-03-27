package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IBajaActivoService;
import com.usic.SistemasActivosFijosUAP.model.entity.BajaActivo;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/baja")
@RequiredArgsConstructor
public class CBajaActivoController {
    
    private final IBajaActivoService bajaActivoService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vistaBajas() {
        return "/seguimiento/baja/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla")
    public String tablaBajas(Model model) {
        List<BajaActivo> bajas = bajaActivoService.findAll();
        model.addAttribute("bajas", bajas);
        return "/seguimiento/baja/tabla_registro";
    }

}
