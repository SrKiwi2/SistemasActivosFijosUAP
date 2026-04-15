package com.usic.SistemasActivosFijosUAP.config;

import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.usic.SistemasActivosFijosUAP.model.entity.ApiError;
import com.usic.SistemasActivosFijosUAP.model.service.hardware.ActivoNoEncontradoException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Manejador global de excepciones para el módulo de Mantenimiento.
 *
 * Principio SRP: centraliza todo el manejo de errores HTTP en un solo lugar
 * Los servicios y controladores solo lanzan excepciones de dominio
 * este componente las traduce a respuestas HTTP estandarizadas
 *
 * Orden de handlers: de más específico a más general
 * Spring usa el handler más específico disponible
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    // =========================================================================
    // 1. EXCEPCIONES DE DOMINIO (propias del módulo)
    // =========================================================================

    /**
     * HTTP 404 — Activo no encontrado.
     * Lanzada desde ActivoMantenimientoServiceImpl cuando el código no existe
     * o no pertenece al grupo EQUIPOS DE COMPUTACION.
     */
    @ExceptionHandler(ActivoNoEncontradoException.class)
    public ResponseEntity<ApiError> handleActivoNoEncontrado(
            ActivoNoEncontradoException ex,
            HttpServletRequest request) {

        log.warn("[404] Activo no encontrado - path: {} | mensaje: {}",
                 request.getRequestURI(), ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                    HttpStatus.NOT_FOUND.value(),
                    HttpStatus.NOT_FOUND.getReasonPhrase(),
                    ex.getMessage(),
                    request.getRequestURI()
                ));
    }

    // =========================================================================
    // 2. ERRORES DE ENTRADA (parámetros y validación)
    // =========================================================================

    /**
     * HTTP 400 — Parámetro requerido ausente en la request.
     *
     * Ejemplo: GET /buscar  (sin ?codigo=...)
     * Spring lanza esta excepción antes de llegar al controller.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleParametroAusente(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        String mensaje = String.format(
            "El parámetro '%s' es obligatorio.", ex.getParameterName()
        );

        log.warn("[400] Parámetro ausente - path: {} | parámetro: {}",
                 request.getRequestURI(), ex.getParameterName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    mensaje,
                    request.getRequestURI()
                ));
    }

    /**
     * HTTP 400 — Tipo incorrecto en parámetro de URL o query param.
     *
     * Ejemplo: GET /activos?page=abc  (se espera Integer)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTipoInvalido(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String tipoEsperado = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "desconocido";

        String mensaje = String.format(
            "El parámetro '%s' recibió el valor '%s'. Se esperaba tipo: %s.",
            ex.getName(), ex.getValue(), tipoEsperado
        );

        log.warn("[400] Tipo de parámetro inválido - path: {} | detalle: {}",
                 request.getRequestURI(), mensaje);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    mensaje,
                    request.getRequestURI()
                ));
    }

    /**
     * HTTP 400 — Violaciones de @Valid en @RequestBody.
     * Retorna el detalle campo a campo en la lista `detalles`.
     *
     * Ejemplo: campo @NotBlank enviado vacío en un body JSON.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidacionBody(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        List<String> detalles = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> String.format("Campo '%s': %s", fe.getField(), fe.getDefaultMessage()))
                .sorted()  // Orden determinístico para facilitar testing
                .toList();

        log.warn("[400] Validación fallida - path: {} | errores: {}",
                 request.getRequestURI(), detalles);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.ofValidacion(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "La solicitud contiene campos inválidos.",
                    request.getRequestURI(),
                    detalles
                ));
    }

    /**
     * HTTP 400 — Violaciones de @Validated en parámetros de controller
     * (como @RequestParam con @Size, @NotBlank, etc.).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request) {

        List<String> detalles = ex.getConstraintViolations()
                .stream()
                .map(cv -> {
                    // Extrae solo el nombre del parámetro, no la ruta completa
                    String campo = cv.getPropertyPath().toString();
                    int lastDot = campo.lastIndexOf('.');
                    String nombreCampo = lastDot >= 0 ? campo.substring(lastDot + 1) : campo;
                    return String.format("Parámetro '%s': %s", nombreCampo, cv.getMessage());
                })
                .sorted()
                .toList();

        log.warn("[400] Violación de constraint - path: {} | errores: {}",
                 request.getRequestURI(), detalles);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.ofValidacion(
                    HttpStatus.BAD_REQUEST.value(),
                    HttpStatus.BAD_REQUEST.getReasonPhrase(),
                    "Parámetros de consulta inválidos.",
                    request.getRequestURI(),
                    detalles
                ));
    }

    // =========================================================================
    // 3. ERRORES DE INFRAESTRUCTURA (BD)
    // =========================================================================

    /**
     * HTTP 503 — Error de acceso a base de datos.
     *
     * Captura excepciones de Spring Data / JDBC sin exponer
     * detalles internos de la BD al consumidor de la API.
     * Los detalles técnicos solo van al log (nivel ERROR).
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccess(
            DataAccessException ex,
            HttpServletRequest request) {

        // Log detallado SOLO en servidor, nunca en el response
        log.error("[503] Error de acceso a datos - path: {} | causa: {}",
                  request.getRequestURI(), ex.getMostSpecificCause().getMessage());

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of(
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                    "No se pudo completar la consulta. Intente nuevamente en unos momentos.",
                    request.getRequestURI()
                ));
    }

    // =========================================================================
    // 4. FALLBACK — Cualquier excepción no manejada
    // =========================================================================

    /**
     * HTTP 500 — Excepción inesperada no contemplada por los handlers anteriores.
     *
     * Regla: NUNCA exponer stack traces ni mensajes internos al cliente.
     * El log.error registra el stack completo para diagnóstico.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(
            Exception ex,
            HttpServletRequest request) {

        log.error("[500] Error inesperado - path: {} | tipo: {} | mensaje: {}",
                  request.getRequestURI(),
                  ex.getClass().getName(),
                  ex.getMessage(),
                  ex);   // El 4to arg imprime el stack trace en el log

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                    "Ocurrió un error interno. Contacte al administrador del sistema.",
                    request.getRequestURI()
                ));
    }
}
