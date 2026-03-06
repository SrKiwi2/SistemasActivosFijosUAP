package com.usic.SistemasActivosFijosUAP.model.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.Data;

@Data
public class RegistroMasivoRequest {
    private Long idResponsable; // Común para todos
    private Long idOrganismoFinanciero; // Opcional
    private LocalDate fechaAdquisicion; // Común
    private List<DetalleRegistroItem> items; // La lista dinámica
}
