package com.usic.SistemasActivosFijosUAP.model.dto.control;

/** Nivel 4 del mapa: un activo imputado a un responsable. */
public record ActivoResponsableDTO(
        Long    idActivo,
        String  codigo,
        String  descripcion,
        String  estadoActivo,
        String  auxiliar,
        Double  costo,
        /** Tiene un hallazgo FALTANTE sin resolver. */
        boolean faltanteAbierto,
        /** Tiene una novedad de condición sin resolver. */
        boolean observadoAbierto
) {}
