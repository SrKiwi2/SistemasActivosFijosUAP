package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDate;
import java.util.List;

/**
 * Registro de faltantes de una persona: los bienes elegidos (de cualquiera de sus oficinas)
 * y el documento que lo respalda. Genera una sola notificación de faltantes.
 */
public record RegistrarFaltantesRequest(
        Long       idPersona,
        List<Long> idsActivos,
        String     documentoRespaldo,
        LocalDate  fechaDocumento,
        String     observacion,
        /** Días hábiles que tiene la persona para informar sobre los bienes (obligatorio). */
        Integer    plazoDias
) {}
