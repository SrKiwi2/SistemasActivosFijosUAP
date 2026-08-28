package com.usic.SistemasActivosFijosUAP.model.dto.control;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Nivel 3 del mapa: un responsable de la oficina. */
public record TileResponsableDTO(
        Long    idResponsable,
        String  codigoFuncionario,
        String  nombre,
        String  ci,
        String  cargo,
        Long    idOficina,
        String  oficina,
        /** {@code _estado = 'ACTIVO'} en la tabla responsable. */
        boolean vigente,
        long    activos,
        long    faltantesAbiertos,
        long    observacionesAbiertas
) {

    /** Derivados, no son componentes del record: Jackson los incluye por la anotación. */
    @JsonProperty("estadoControl")
    public EstadoControl estadoControl() {
        return EstadoControl.de(faltantesAbiertos, 0, activos > 0 ? 1 : 0);
    }

    /**
     * Un responsable dado de baja que todavía figura como custodio de activos.
     * Es la inconsistencia que este módulo existe para sacar a la luz: nadie
     * responde por esos bienes y no aparece en ningún reporte de vigentes.
     */
    @JsonProperty("anomalia")
    public boolean esAnomalia() {
        return !vigente && activos > 0;
    }
}
