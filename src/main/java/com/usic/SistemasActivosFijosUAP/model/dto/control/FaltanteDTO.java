package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDateTime;

/** Un hallazgo de la vista Faltantes, ya resuelto contra su responsable. */
public record FaltanteDTO(
        Long          idHallazgo,
        /** FALTANTE | SOBRANTE | OBSERVADO | SIN_CODIFICAR | DESACUERDO_DATOS */
        String        tipoHallazgo,
        /** ABIERTO | RESUELTO */
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
        Long          idInventario,
        String        numeroInventario,
        LocalDateTime fechaDeteccion,
        String        descripcionDiscrepancia,
        String        tipoResolucion,
        String        accionCorrectiva,
        LocalDateTime fechaResolucion,
        String        usuarioRevisor
) {}
