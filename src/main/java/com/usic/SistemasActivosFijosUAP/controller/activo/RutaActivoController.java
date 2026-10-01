package com.usic.SistemasActivosFijosUAP.controller.activo;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.service.control.ReglaNegocioException;
import com.usic.SistemasActivosFijosUAP.model.service.seguimiento.RutaActivoService;

import lombok.extern.slf4j.Slf4j;

/**
 * Seguimiento de Activo: se escribe un código y se ve por qué oficinas y responsables
 * pasó el bien, con fechas y documentos. Menú "Historial → Seguimiento de Activo (ruta)"
 * (permiso opcion_ruta_activo, rutaBase /administracion/ruta-activo).
 */
@Slf4j
@Controller
@RequestMapping("/administracion/ruta-activo")
public class RutaActivoController {

    private final RutaActivoService rutaActivoService;

    public RutaActivoController(RutaActivoService rutaActivoService) {
        this.rutaActivoService = rutaActivoService;
    }

    /** {@code ?codigo=} abre la pantalla ya buscando ese activo (desde otros módulos). */
    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista(Model model, @RequestParam(value = "codigo", required = false) String codigo) {
        model.addAttribute("codigoInicial", codigo);
        return "activo/rutaActivo";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/datos")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> datos(@RequestParam("codigo") String codigo) {
        try {
            return ResponseEntity.ok(rutaActivoService.ruta(codigo));
        } catch (ReglaNegocioException e) {
            return ResponseEntity.ok(Map.of("ok", false, "msg", e.getMessage()));
        } catch (Exception e) {
            log.error("Error armando la ruta del activo {}", codigo, e);
            return ResponseEntity.ok(Map.of("ok", false, "msg", "No se pudo armar el seguimiento: " + e.getMessage()));
        }
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/sugerencias")
    @ResponseBody
    public List<Map<String, Object>> sugerencias(@RequestParam("q") String q) {
        return rutaActivoService.sugerencias(q);
    }
}
