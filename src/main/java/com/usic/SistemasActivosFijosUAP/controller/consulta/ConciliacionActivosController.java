package com.usic.SistemasActivosFijosUAP.controller.consulta;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.usic.SistemasActivosFijosUAP.anotacion.ValidarUsuarioAutenticado;
import com.usic.SistemasActivosFijosUAP.model.service.ConciliacionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Módulo de Conciliación BD↔DBF (solo lectura) para ADMINISTRADOR y SUPER USUARIO.
 *
 * Muestra las divergencias entre los activos de PostgreSQL y el {@code ACTUAL.DBF}
 * del VSIAF: lo que está en la BD pero no en el DBF (típicamente PENDIENTE de
 * sincronizar, o una anomalía si está ACTIVO) y lo que está en el DBF pero no en
 * la BD. No modifica datos; solo expone la información para revisión manual.
 *
 * La vista se carga como fragmento del SPA (#contenido) y los datos se piden de
 * forma asíncrona a {@code /administracion/conciliacion/datos} (la lectura del DBF
 * puede tardar unos segundos).
 */
@Slf4j
@Controller
@RequestMapping("/administracion/conciliacion")
@RequiredArgsConstructor
public class ConciliacionActivosController {

    private final ConciliacionService conciliacionService;

    @ValidarUsuarioAutenticado
    @GetMapping("/vista")
    public String vista() {
        return "conciliacion/activos";
    }

    @ValidarUsuarioAutenticado
    @GetMapping("/datos")
    @ResponseBody
    public ResponseEntity<?> datos() {
        try {
            return ResponseEntity.ok(conciliacionService.calcular());
        } catch (Exception e) {
            log.error("Error calculando conciliación BD↔DBF: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "ok", false,
                "message", "No se pudo leer el DBF del VSIAF: " + e.getMessage()
            ));
        }
    }
}
