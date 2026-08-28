package com.usic.SistemasActivosFijosUAP.model.dto.control;

/**
 * Resultado de aplicar un lote de marcas.
 *
 * <p>{@code ignoradas} no es un error: son marcas que el servidor ya tenía o que
 * llegaron más viejas que la registrada. La app las puede borrar de su cola igual.
 */
public record ResumenMarcasDTO(
        boolean ok,
        int     aplicadas,
        int     ignoradas,
        int     sobrantes,
        int     encontrados,
        int     pendientes,
        int     esperados
) {}
