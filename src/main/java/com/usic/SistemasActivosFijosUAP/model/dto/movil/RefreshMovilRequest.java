package com.usic.SistemasActivosFijosUAP.model.dto.movil;

import jakarta.validation.constraints.NotBlank;

/** Petición de renovación de sesión desde la app móvil. */
public record RefreshMovilRequest(

        @NotBlank(message = "El refreshToken es obligatorio")
        String refreshToken
) {}
