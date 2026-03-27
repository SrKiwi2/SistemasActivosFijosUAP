package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.IService.IAsignacionService;
import com.usic.SistemasActivosFijosUAP.model.IService.IDetalleAsignacionActivoService;
import com.usic.SistemasActivosFijosUAP.model.entity.Asignacion;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/asignacion")
@RequiredArgsConstructor
public class CAsignacionActivoController {

    private final IAsignacionActivoService asignacionActivoService;
    private final IDetalleAsignacionActivoService detalleAsignacionActivoService;


    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista_activos_nuevos() {
        return "/seguimiento/asignacion/vista";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla_activos_nuevos")
    public String tabla_activos_nuevos(Model model) {
        List<AsignacionActivo> asignaciones = asignacionActivoService.findAll();
        model.addAttribute("asignaciones", asignaciones);
        return "/seguimiento/asignacion/tabla_registro";
    }

    // @GetMapping("/{id}/detalles-json")
    // @ResponseBody
    // public List<DetalleAsignacionActivo> detallesJson(@PathVariable Long id) { 

    //  }
}
