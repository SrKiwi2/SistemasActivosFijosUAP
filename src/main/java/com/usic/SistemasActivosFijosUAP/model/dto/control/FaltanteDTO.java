package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Un hallazgo de la vista Faltantes, ya resuelto contra su responsable. */
public record FaltanteDTO(
        Long          idHallazgo,
        /** FALTANTE | SOBRANTE | OBSERVADO | SIN_CODIFICAR | DESACUERDO_DATOS */
        String        tipoHallazgo,
        /** ABIERTO | EN_CUSTODIA | RESUELTO */
        String        estadoHallazgo,
        Long          idActivo,
        String        codigo,
        String        descripcion,
        Long          idResponsable,
        String        responsable,
        String        codigoFuncionario,
        Long          idOficina,
        String        oficina,
        Long          idPredio,
        String        predio,
        /** Null si el faltante se registró directo, sin levantamiento. */
        Long          idInventario,
        String        numeroInventario,
        LocalDateTime fechaDeteccion,
        String        descripcionDiscrepancia,
        String        tipoResolucion,
        String        accionCorrectiva,
        LocalDateTime fechaResolucion,
        String        usuarioRevisor,
        /** LEVANTAMIENTO | DIRECTO (null en hallazgos anteriores a la custodia) */
        String        origen,
        String        documentoRespaldo,
        LocalDate     fechaDocumento,
        /** Responsable de custodia del predio; null mientras no se envíe. */
        Long          idResponsableCustodia,
        LocalDateTime fechaEnvioCustodia,
        String        usuarioEnvioCustodia,
        /** Persona a la que se imputa: una misma persona es responsable en varias oficinas. */
        Long          idPersona,
        String        ci,
        Short         codOfi,
        String        unidad,
        /** Acta de faltantes en la que se registró; null si todavía no se registró. */
        Long          idActa,
        String        numeroActa,
        /** ESPERANDO_ALTA | ENVIADO | CONFIRMADO | ERROR; null si no se pidió el traslado. */
        String        estadoEnvio,
        String        mensajeEnvio,
        /** Histórico regularizado sin oficina de origen conocida (apunta a la oficina de faltantes). */
        boolean       origenNoRegistrado,
        /** Plazo en días hábiles de la notificación (extraído del contenido JSON del acta). */
        Integer       plazoDias
) {}
