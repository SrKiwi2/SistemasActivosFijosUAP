package com.usic.SistemasActivosFijosUAP.controller.consulta;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.IService.IOficinaService;
import com.usic.SistemasActivosFijosUAP.model.IService.IPredioServicio;

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
 * El select de Responsable ya no se precarga aquí (antes eran ~2400 filas
 * embebidas en la página, sin filtrar por oficina): se busca en vivo contra
 * {@code GET /api/responsables/buscar} (Select2 + paginado).
 *
 * Aquí solo se entregan predios y oficinas para los selects de filtro. El
 * control de rol se hace en la plantilla con session.nombre_rol (igual que el
 * sidebar).
 */
@Controller
@RequestMapping("/administracion/consulta")
@RequiredArgsConstructor
public class ConsultaActivosController {

    private final IPredioServicio predioServicio;
    private final IOficinaService oficinaService;

    @ValidarUsuarioAutenticado
    @GetMapping("/activos/vista")
    public String consultaActivos(Model model) {
        model.addAttribute("predios", predioServicio.findAll());
        model.addAttribute("oficinas", oficinaService.listarOficinas());
        return "consulta/activos";
    }
}
