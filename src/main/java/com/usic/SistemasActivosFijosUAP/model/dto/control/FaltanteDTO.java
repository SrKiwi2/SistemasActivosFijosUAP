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
        String        usuarioEnvioCustodia
) {}
