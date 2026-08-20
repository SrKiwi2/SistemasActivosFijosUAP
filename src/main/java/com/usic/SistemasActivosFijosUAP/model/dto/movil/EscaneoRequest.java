package com.usic.SistemasActivosFijosUAP.model.dto.movil;

import jakarta.validation.constraints.NotBlank;

/** Lo que la app envía tras leer una etiqueta o tras teclear un código. */
public record EscaneoRequest(

        /** Texto crudo del QR, o el código tal como lo escribió el usuario. */
        @NotBlank(message = "El contenido del escaneo es obligatorio")
        String payload,

        /** CAMARA | MANUAL — cambia cómo se interpreta el texto. */
        String origen
) {

    public boolean esManual() {
        return "MANUAL".equalsIgnoreCase(origen);
    }
}
