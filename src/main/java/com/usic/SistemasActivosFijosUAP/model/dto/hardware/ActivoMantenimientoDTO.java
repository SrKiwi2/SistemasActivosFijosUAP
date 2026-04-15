package com.usic.SistemasActivosFijosUAP.model.dto.hardware;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ActivoMantenimientoDTO {
 // --- Identificación ---
    private Long idActivo;
    private String codigo;
    private String codigoSec;
    private String nombre;
    private String descripcion;

    // --- Ubicación ---
    private String oficinaNombre;

    // --- Responsabilidad ---
    private String responsableNombre;
    private String codigoFuncionario;

    // --- Ciclo de Vida ---
    private LocalDate fechaAdquisicion;
    private BigDecimal vidaUtil;

    // --- Estado ---
    private String estadoNombre;

    // --- Integridad ---
    private String hashDatos;
}
