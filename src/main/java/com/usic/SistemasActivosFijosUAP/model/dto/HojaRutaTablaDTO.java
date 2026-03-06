package com.usic.SistemasActivosFijosUAP.model.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HojaRutaTablaDTO {
    private Long idHojaRuta;
    private String codigo;
    private String tipo;
    private Integer gestion;
    private String solicitanteNombre;
    private String solicitanteCargo;
    private String descripcion;
    private String certificacion;
    private BigDecimal monto;
    private String estadoActual;
    private String unidadOrigenNombre;
}
