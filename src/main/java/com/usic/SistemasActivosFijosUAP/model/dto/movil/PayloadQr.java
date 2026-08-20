package com.usic.SistemasActivosFijosUAP.model.dto.movil;

import java.util.List;

/**
 * Resultado de interpretar el contenido crudo de una etiqueta QR.
 *
 * <p>Formato real de las etiquetas (6 campos separados por {@code |}):
 * <pre>
 * UAP|COBIJA|CAMPUS UNIVERSITARIO LAS PALMAS|MUEBLES Y ENSERES|148-01-04-02-03609|MUEBLE PARA COMPUTADORA…
 *  1 |   2  |               3               |         4       |        5         |            6
 * </pre>
 *
 * <p>El campo 5 es el <b>código visual</b>: {@code 148} es el código de la
 * entidad ({@code Entidad.entidadCodigo}) y no forma parte de
 * {@code Activo.codigo}, que en la BD es {@code 01-04-02-03609}.
 *
 * <p>Los textos de los campos 1–4 y 6 son <b>lo que se imprimió el día de la
 * etiqueta</b> y pueden haber quedado obsoletos. Sirven para detectar
 * diferencias, nunca como dato bueno.
 */
public record PayloadQr(

        /** Texto tal cual lo devolvió el lector. */
        String crudo,

        /** Campos separados por {@code |} (vacío si la etiqueta no los trae). */
        List<String> campos,

        String siglaEntidad,
        String municipio,
        String predio,
        String grupoContable,
        String descripcion,

        /** Código tal como está impreso, con prefijo: {@code 148-01-04-02-03609}. */
        String codigoVisual,

        /** Prefijo de entidad ({@code 148}) o {@code null} si la etiqueta no lo trae. */
        String prefijoEntidad,

        /** Código como está en la BD, ya sin prefijo: {@code 01-04-02-03609}. */
        String codigo,

        String codMunicipio,
        String codPredio,
        String codGrupo,
        String correlativo,

        /** false = no se pudo extraer ningún código reconocible del contenido. */
        boolean legible,

        /**
         * true cuando el usuario escribió solo el número correlativo
         * ({@code 3609}): hay que buscar candidatos, no hay código completo.
         */
        boolean soloCorrelativo
) {

    public static PayloadQr ilegible(String crudo) {
        return new PayloadQr(crudo, List.of(), null, null, null, null, null,
                null, null, null, null, null, null, null, false, false);
    }
}
