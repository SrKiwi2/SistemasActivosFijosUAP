package com.usic.SistemasActivosFijosUAP.model.dto.movil;

/**
 * Diferencia entre lo que dice la etiqueta y lo que dice la base de datos.
 *
 * <p>Se clasifica por <b>capa</b> porque no todas las diferencias significan lo
 * mismo, y tratarlas igual confundiría al operador:
 *
 * <ol>
 *   <li><b>Capa 1 — texto de la etiqueta.</b> Lo impreso quedó obsoleto: cambió
 *       la descripción, se reclasificó el grupo contable, se renombró el predio.
 *       Se corrige reimprimiendo la etiqueta.</li>
 *   <li><b>Capa 2 — segmentos del código.</b> El código lleva dentro municipio,
 *       predio y grupo del momento en que se emitió, y es inmutable. Si el
 *       activo se transfirió, el código ya no coincide con su ubicación actual.
 *       Es <b>normal</b>, no un error: explica por qué la etiqueta y la realidad
 *       difieren.</li>
 *   <li><b>Capa 3 — existencia y estado.</b> No está, está cancelado, dado de
 *       baja o pendiente de subir al VSIAF.</li>
 * </ol>
 */
public record DiscrepanciaDTO(

        int    capa,
        /** Nombre técnico del campo: descripcion, predio, grupoContable… */
        String campo,
        /** Etiqueta legible para la pantalla: "Descripción", "Predio"… */
        String etiqueta,
        String valorQr,
        String valorSistema,
        /** INFO · AVISO · ERROR */
        String severidad
) {

    public static DiscrepanciaDTO info(int capa, String campo, String etiqueta, String qr, String sistema) {
        return new DiscrepanciaDTO(capa, campo, etiqueta, qr, sistema, "INFO");
    }

    public static DiscrepanciaDTO aviso(int capa, String campo, String etiqueta, String qr, String sistema) {
        return new DiscrepanciaDTO(capa, campo, etiqueta, qr, sistema, "AVISO");
    }
}
