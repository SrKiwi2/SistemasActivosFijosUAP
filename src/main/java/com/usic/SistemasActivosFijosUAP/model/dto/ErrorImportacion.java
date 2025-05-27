package com.usic.SistemasActivosFijosUAP.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ErrorImportacion {
    private int fila;
    private String motivo;
}
