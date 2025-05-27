package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ResultadoImportacionDTO {
    private int exitosos;
    private List<ErrorImportacion> errores;
    private String mensaje;
}
