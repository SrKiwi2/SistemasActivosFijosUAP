package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.ITransferenciaService;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.Transferencia;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/transferencia")
@RequiredArgsConstructor
public class CTransferenciaActivoController {

    private final ITransferenciaService transferenciaService;
    
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_activos_trans() {
        return "/seguimiento/transferencia/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla_transferencias")
    public String tabla_activos_trans(Model model) {
        List<Transferencia> asignaciones = transferenciaService.findAll();
        model.addAttribute("transferencias", asignaciones);
        return "/seguimiento/transferencia/tabla_registro";
    }
}
