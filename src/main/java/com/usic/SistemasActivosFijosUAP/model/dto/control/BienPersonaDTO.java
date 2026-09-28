package com.usic.SistemasActivosFijosUAP.model.dto.control;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Un bien a cargo de una persona, con su ubicación, para elegir los faltantes. La pantalla
 * los agrupa por predio → oficina.
 */
public record BienPersonaDTO(
        Long    idActivo,
        String  codigo,
        String  descripcion,
        String  estadoActivo,
        Long    idResponsable,
        String  codigoFuncionario,
        String  cargo,
        Long    idOficina,
        Short   codOfi,
        String  oficina,
        Long    idPredio,
        String  unidad,
        String  predio,
        boolean bloqueado,
        /** Faltante pendiente del bien, si lo hay. */
        Long    idHallazgo,
        String  estadoHallazgo,
        /** Acta en la que ya se registró; null si el faltante está ABIERTO sin registrar. */
        String  numeroActa,
        /** Levantamiento que lo detectó, si vino de uno. */
        String  numeroInventario
) {

    /** Se puede incluir en un acta nueva: libre, o faltante de un levantamiento sin registrar. */
    @JsonProperty("seleccionable")
    public boolean seleccionable() {
        return !bloqueado && numeroActa == null;
    }
}
