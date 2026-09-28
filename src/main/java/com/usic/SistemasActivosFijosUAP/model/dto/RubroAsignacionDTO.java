package com.usic.SistemasActivosFijosUAP.model.dto;

import java.util.List;

/**
 * Qué contiene un acta, en términos de grupo contable y auxiliar.
 *
 * <p>Una misma acta puede mezclar rubros, así que se guarda una línea por combinación
 * (grupo, auxiliar) con su cantidad, ordenadas de mayor a menor. El listado muestra las
 * primeras y resume el resto, en vez de elegir una sola y dar a entender que el acta es
 * de un solo rubro.
 */
public record RubroAsignacionDTO(
        String grupoContable,
        String auxiliar,
        long   cantidad
) {

    private static final String SIN_DATO = "Sin clasificar";

    public static RubroAsignacionDTO desdeFila(Object[] fila) {
        return new RubroAsignacionDTO(
                texto(fila[1]),
                texto(fila[2]),
                (fila[3] instanceof Number n) ? n.longValue() : 0L);
    }

    /** El id del acta al que pertenece la fila, para poder agruparlas. */
    public static Long idAsignacionDe(Object[] fila) {
        return ((Number) fila[0]).longValue();
    }

    private static String texto(Object o) {
        String s = (o == null) ? null : o.toString().trim();
        return (s == null || s.isEmpty()) ? SIN_DATO : s;
    }

    /** "3 SILLA GIRATORIA" — lo que se muestra en cada chip del listado. */
    public String etiqueta() {
        return cantidad + " " + auxiliar;
    }

    /** Grupos contables distintos del acta, en orden de aparición (el de más bienes primero). */
    public static List<String> gruposDe(List<RubroAsignacionDTO> rubros) {
        return rubros.stream().map(RubroAsignacionDTO::grupoContable).distinct().toList();
    }
}
