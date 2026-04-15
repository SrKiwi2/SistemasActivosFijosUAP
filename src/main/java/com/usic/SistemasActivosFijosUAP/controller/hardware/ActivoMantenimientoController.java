package com.usic.SistemasActivosFijosUAP.controller.hardware;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.usic.SistemasActivosFijosUAP.model.dto.hardware.ActivoMantenimientoDTO;
import com.usic.SistemasActivosFijosUAP.model.service.hardware.IActivoMantenimientoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/mantenimiento/activos")
@RequiredArgsConstructor
public class ActivoMantenimientoController {
    private final IActivoMantenimientoService activoService;

    @GetMapping
    public ResponseEntity<Page<ActivoMantenimientoDTO>> listarEquipos(
        @PageableDefault(
            size = 20,
            sort = "codigo",
            direction = Sort.Direction.ASC
        )
        Pageable pageable
    ) {
        Page<ActivoMantenimientoDTO> equipos = activoService.listarEquiposComputacion(pageable);
        return ResponseEntity.ok(equipos);
    }

    @GetMapping("/buscar")
    public ResponseEntity<ActivoMantenimientoDTO> buscarPorCodigo(
        @RequestParam String codigo
    ) {
        ActivoMantenimientoDTO activo = activoService.buscarPorCodigo(codigo);
        return ResponseEntity.ok(activo);
    }
}
