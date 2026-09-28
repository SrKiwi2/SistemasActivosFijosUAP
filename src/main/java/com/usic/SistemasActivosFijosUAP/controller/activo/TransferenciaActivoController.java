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

    /**
     * Vista única de transferencias (interna + externa). El tipo lo decide el movimiento:
     * si el predio destino es distinto al del activo es externa, y ahí hay que reubicar su
     * auxiliar. Tenerlas separadas dejaba registrar una externa por la pantalla interna
     * (sin tocar el auxiliar) y obligaba a mantener dos pantallas casi iguales.
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/transferencia")
    public String transferencia(Model model) {
        model.addAttribute("predios", predioServicio.listarPredios());
        return "activo/transferencia";
    }

    /** Rutas anteriores: quedan apuntando a la vista unificada (enlaces y permisos viejos). */
    @ValidarUsuarioAutenticado
    @GetMapping("/trasnferenciaInterna")
    public String trasnferenciaInterna(Model model) {
        return transferencia(model);
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/trasnferenciaExterna")
    public String transferenciaExterna(Model model) {
        return transferencia(model);
    }
}
