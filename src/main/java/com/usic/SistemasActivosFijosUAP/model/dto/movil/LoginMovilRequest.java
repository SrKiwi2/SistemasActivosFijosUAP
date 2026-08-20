package com.usic.SistemasActivosFijosUAP.model.dto.movil;

import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales que envía la app móvil al iniciar sesión.
 * Son las mismas del sistema web: usuario + contraseña BCrypt.
 */
public record LoginMovilRequest(

        @NotBlank(message = "El usuario es obligatorio")
        String usuario,

        @NotBlank(message = "La contraseña es obligatoria")
        String contrasena,

        /** Identificador estable que genera la app en su primera ejecución. */
        String deviceId,

        /** android | ios | web */
        String plataforma,

        String modelo,

        String appVersion
) {}
