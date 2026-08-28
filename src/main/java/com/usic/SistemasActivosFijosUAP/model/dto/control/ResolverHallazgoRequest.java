package com.usic.SistemasActivosFijosUAP.model.dto.control;

import jakarta.validation.constraints.NotBlank;

/**
 * Cierre de un hallazgo.
 *
 * <p>La acción correctiva es obligatoria: un faltante que se cierra sin decir
 * por qué no deja rastro de nada, que es justo lo contrario de para qué existe
 * este módulo.
 */
public record ResolverHallazgoRequest(
        /** APARECIO | JUSTIFICADO | DERIVADO_BAJA */
        @NotBlank(message = "Indique con qué se resuelve el hallazgo")
        String tipoResolucion,
        @NotBlank(message = "Describa la acción correctiva")
        String accionCorrectiva
) {}
