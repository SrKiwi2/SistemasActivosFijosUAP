package com.usic.SistemasActivosFijosUAP.model.dto.movil;

/**
 * Respuesta de {@code /auth/login} y {@code /auth/refresh}.
 *
 * <p>El {@code accessToken} (JWT) caduca en horas; el {@code refreshToken} no
 * caduca por tiempo y se rota en cada renovación. Esa pareja es lo que permite
 * que el usuario quede "logueado siempre" hasta que cierre sesión o le revoquen
 * el dispositivo.
 */
public record SesionMovilResponse(
        String          accessToken,
        String          refreshToken,
        /** Segundos de vida restantes del accessToken. */
        long            expiraEn,
        UsuarioMovilDTO usuario
) {}
