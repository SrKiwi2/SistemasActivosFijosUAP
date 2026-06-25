package com.usic.SistemasActivosFijosUAP.controller.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.IService.IActivoService;
import com.usic.SistemasActivosFijosUAP.model.dto.ActivoFichaDTO;

import lombok.extern.slf4j.Slf4j;

/**
 * API pública de consulta de activos por código.
 *
 * Endpoint principal:
 *   GET /api/activos/{codigo}/ficha
 *
 * Dado el código de un activo (ej. "01-01-08-00583") devuelve, en JSON,
 * el responsable, la oficina, la unidad (predio) y la descripción del activo.
 *
 * Las rutas {@code /api/**} están en {@code permitAll()} en SeguridadConfig,
 * por lo que este endpoint queda accesible para sistemas externos.
 */
@Slf4j
@RestController
@RequestMapping("/api/activos")
public class ActivoConsultaApiController {

    private final IActivoService activoService;

    public ActivoConsultaApiController(IActivoService activoService) {
        this.activoService = activoService;
    }

    @GetMapping("/{codigo}/ficha")
    @Transactional(readOnly = true)
    public ResponseEntity<ActivoFichaDTO> obtenerFichaPorCodigo(@PathVariable String codigo) {
        return activoService.fetchFullByCodigo(codigo)
                .map(a -> {
                    String oficina = a.getOficina() != null
                            ? a.getOficina().getNombre()
                            : null;

                    String unidad = (a.getOficina() != null && a.getOficina().getPredio() != null)
                            ? a.getOficina().getPredio().getUnidad()
                            : null;

                    String responsable = (a.getResponsable() != null && a.getResponsable().getPersona() != null)
                            ? a.getResponsable().getPersona().getNombreCompleto()
                            : null;

                    return ResponseEntity.ok(new ActivoFichaDTO(
                            a.getCodigo(),
                            responsable,
                            oficina,
                            unidad,
                            a.getDescripcion()
                    ));
                })
                .orElseGet(() -> {
                    log.info("[API-FICHA] Activo no encontrado para codigo='{}'", codigo);
                    return ResponseEntity.notFound().build();
                });
    }
}
