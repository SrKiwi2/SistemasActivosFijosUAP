package com.usic.SistemasActivosFijosUAP.controller.consulta;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;
import com.usic.SistemasActivosFijosUAP.model.IService.IResponsableService;

import lombok.RequiredArgsConstructor;

/**
 * Consulta de Activos (solo lectura) para ADMINISTRADOR, SUPER USUARIO y APOYO.
 *
 * La vista se carga como fragmento dentro del SPA (#contenido) y los datos se
 * obtienen reutilizando el endpoint server-side ya existente
 * {@code POST /administracion/activo/datatables} ({@code buscarConFiltros}),
 * que pagina y filtra por búsqueda (nombre/código), código, oficina,
 * responsable y fecha (siempre sobre activos en estado ACTIVO).
 *
 * Aquí solo se entregan los catálogos para los selects de filtro. El control de
 * rol se hace en la plantilla con session.nombre_rol (igual que el sidebar).
 */
@Controller
@RequestMapping("/administracion/consulta")
@RequiredArgsConstructor
public class ConsultaActivosController {

    private final IPredioServicio predioServicio;
    private final IOficinaService oficinaService;
    private final IResponsableService responsableService;

    @ValidarUsuarioAutenticado
    @GetMapping("/activos/vista")
    public String consultaActivos(Model model) {
        model.addAttribute("predios", predioServicio.findAll());
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        model.addAttribute("responsables", responsableService.listarResponsables());
        return "consulta/activos";
    }
}
