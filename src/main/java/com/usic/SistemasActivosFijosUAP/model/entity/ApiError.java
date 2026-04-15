package com.usic.SistemasActivosFijosUAP.model.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime timestamp,

    int             status,
    String          error,        // Categoría HTTP: "Not Found", "Bad Request", etc.
    String          mensaje,      // Mensaje legible para el consumidor de la API
    String          path,         // Endpoint que originó el error
    List<String>    detalles      // Solo presente en errores de validación (campo a campo)

) {
    /** Factory method para construcción limpia sin detalles. */
    public static ApiError of(int status, String error, String mensaje, String path) {
        return new ApiError(LocalDateTime.now(), status, error, mensaje, path, null);
    }

    /** Factory method para errores de validación con lista de campos. */
    public static ApiError ofValidacion(int status, String error,
                                        String mensaje, String path,
                                        List<String> detalles) {
        return new ApiError(LocalDateTime.now(), status, error, mensaje, path, detalles);
    }
}
