package com.usic.SistemasActivosFijosUAP.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    /** RECIBIDO / ENVIADO / ARCHIVADO del último movimiento, o SIN MOVIMIENTOS. */
    private String estadoActual;
    /** Unidad destino del último movimiento: dónde está el documento ahora. */
    private String ubicacionActual;
    /** Fecha del último movimiento. */
    private LocalDate fechaUltimo;
    private int movimientos;
}
