package com.usic.SistemasActivosFijosUAP.model.dto.control;

/** Lo que dejó el cierre. La app lo muestra como comprobante del recorrido. */
public record ResumenCierreDTO(
        boolean ok,
        Long    idInventario,
        String  numeroInventario,
        int     esperados,
        int     encontrados,
        int     faltantes,
        int     observados,
        int     hallazgosCreados
) {}
