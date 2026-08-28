package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Nivel 2 del mapa: una oficina del predio. */
public record TileOficinaDTO(
        Long          idOficina,
        Short         codOfi,
        String        nombre,
        Long          idPredio,
        String        predio,
        long          responsables,
        long          activos,
        long          faltantesAbiertos,
        long          levantamientosTotales,
        /** Levantamiento abierto en esta oficina, si lo hay. */
        Long          idLevantamientoEnCurso,
        LocalDateTime ultimoLevantamiento,
        Integer       ultimoEncontrados,
        Integer       ultimoEsperados
) {

    /** Derivados, no son componentes del record: Jackson los incluye por la anotación. */
    @JsonProperty("estadoControl")
    public EstadoControl estadoControl() {
        return EstadoControl.de(
                faltantesAbiertos,
                idLevantamientoEnCurso != null ? 1 : 0,
                levantamientosTotales);
    }

    /** Para la barra de avance del tile mientras el recorrido está abierto. */
    @JsonProperty("porcentajeAvance")
    public int porcentajeAvance() {
        if (ultimoEsperados == null || ultimoEsperados == 0) return 0;
        int enc = ultimoEncontrados == null ? 0 : ultimoEncontrados;
        return (int) Math.round(enc * 100.0 / ultimoEsperados);
    }
}
