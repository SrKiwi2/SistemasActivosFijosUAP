package com.usic.SistemasActivosFijosUAP.controller.Seguimieto;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IBajaActivoService;
import com.usic.SistemasActivosFijosUAP.model.dao.IBajaActivoDao;
import com.usic.SistemasActivosFijosUAP.model.entity.BajaActivo;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/administracion/baja")
@RequiredArgsConstructor
public class CBajaActivoController {

    private final IBajaActivoService bajaActivoService;
    private final IBajaActivoDao bajaActivoDao;

    /**
     * Ruta anterior: la vista de seguimiento vieja (seguimiento/baja/vista.html) estaba hecha
     * para otro modelo de bajas y ya no funcionaba (detalle vacío, PDF y exportaciones a
     * endpoints inexistentes). Queda apuntando al módulo vigente por si algún menú la usa.
     */
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vistaBajas() {
        return "operaciones/baja/modulo";
    }

    // Vista dedicada del módulo: registro de baja (con informe adjunto) + seguimiento.
    @ValidarUsuarioAutenticado
    @GetMapping("/modulo")
    public String moduloBajas() {
        return "operaciones/baja/modulo";
    }

    @ValidarUsuarioAutenticado
    @PostMapping("/tabla")
    public String tablaBajas(Model model) {
        List<BajaActivo> bajas = bajaActivoDao.listarParaTabla();
        model.addAttribute("bajas", bajas);
        return "/seguimiento/baja/tabla_registro";
    }

}
