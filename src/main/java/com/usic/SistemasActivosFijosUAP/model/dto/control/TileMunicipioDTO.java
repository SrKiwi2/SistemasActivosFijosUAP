package com.usic.SistemasActivosFijosUAP.model.dto.control;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Nivel 0 del mapa: un municipio como cuadrado, agrupando sus predios.
 *
 * <p>{@code idMunicipio} puede venir en null: la FK {@code predio.id_municipio} es
 * opcional, así que los predios sin municipio se juntan en un cuadrado aparte en vez
 * de desaparecer del mapa. Ese cuadrado se navega con {@code idMunicipio = null} y es
 * justamente la señal de que a esos predios les falta cargar el municipio.
 */
public record TileMunicipioDTO(
        Long   idMunicipio,
        String nombre,
        String codigo,
        long   predios,
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

    /** Los predios que todavía no tienen municipio cargado. */
    @JsonProperty("sinMunicipio")
    public boolean sinMunicipio() {
        return idMunicipio == null;
    }
}
