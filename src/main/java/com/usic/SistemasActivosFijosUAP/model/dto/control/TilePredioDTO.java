package com.usic.SistemasActivosFijosUAP.model.dto.control;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Nivel 1 del mapa: un predio como cuadrado. */
public record TilePredioDTO(
        Long   idPredio,
        String descrip,
        String unidad,
        String ciudad,
        long   oficinas,
        long   responsables,
        long   activos,
        long   faltantesAbiertos,
        long   levantamientosEnCurso,
        long   levantamientosTotales
) {

    /** Derivado, no es componente del record: Jackson lo incluye por la anotación. */
    @JsonProperty("estadoControl")
    public EstadoControl estadoControl() {
        return EstadoControl.de(faltantesAbiertos, levantamientosEnCurso, levantamientosTotales);
    }
}
