package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.util.List;

/**
 * Lo que devuelve el buscador de activos.
 *
 * <p>Trae {@code total} además de la lista porque el resultado viene recortado: hay más
 * de 31.000 activos y una búsqueda por una palabra común puede alcanzar a miles. Decir
 * "mostrando 200 de 1.340" es honesto; devolver 1.340 filas y que el navegador se
 * arrastre, o devolver 200 sin avisar que había más, no.
 *
 * @param total      cuántos activos cumplen la búsqueda en total
 * @param resultados los que efectivamente se devuelven (como mucho el tope pedido)
 * @param truncado   true si {@code total} es mayor que lo devuelto
 */
public record ResultadoBusquedaDTO(
        long total,
        boolean truncado,
        List<ActivoUbicacionDTO> resultados
) {
    public static ResultadoBusquedaDTO de(long total, List<ActivoUbicacionDTO> resultados) {
        return new ResultadoBusquedaDTO(total, total > resultados.size(), resultados);
    }

    /** Sin texto de búsqueda no se devuelve nada: el buscador no lista los 31.000 bienes. */
    public static ResultadoBusquedaDTO vacio() {
        return new ResultadoBusquedaDTO(0, false, List.of());
    }
}
