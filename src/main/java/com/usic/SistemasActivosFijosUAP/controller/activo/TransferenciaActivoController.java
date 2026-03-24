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

@Controller
@RequestMapping("/administracion/trasnferencia")
@RequiredArgsConstructor
public class TransferenciaActivoController {
    
    private final IPredioServicio predioServicio;

    @ValidarUsuarioAutenticado
    @GetMapping("/trasnferenciaInterna")
    public String trasnferenciaInterna(Model model) {
        List<Predio> listarDePredio = predioServicio.listarPredios();
        model.addAttribute("predios", listarDePredio);
        return "activo/transferenciaInterna";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/trasnferenciaExterna")
    public String transferenciaExterna() {
        return "activo/transferenciaExterna";
    }
}
