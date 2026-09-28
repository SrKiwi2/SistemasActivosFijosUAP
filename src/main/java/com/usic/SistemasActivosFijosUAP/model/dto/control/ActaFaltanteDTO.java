package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Un acta de faltantes tal como se emitió (sale de la foto guardada, no de los datos de
 * hoy): la usan el PDF y la página pública de verificación, así la reimpresión y la
 * verificación dicen lo mismo que el papel firmado.
 */
public record ActaFaltanteDTO(
        Long          idActa,
        String        numero,
        String        token,
        LocalDateTime fechaEmision,
        String        usuarioEmision,
        String        personaNombre,
        String        personaCi,
        String        personaCargo,
        String        documentoRespaldo,
        LocalDate     fechaDocumento,
        String        observacion,
        int           totalBienes,
        /** VIGENTE | ANULADA | RESUELTA */
        String        estado,
        String        motivoAnulacion,
        LocalDateTime fechaAnulacion,
        String        hash,
        /** El hash guardado coincide con el contenido guardado. */
        boolean       integro,
        List<Predio>  predios,
        /** FALTANTES | REGULARIZACION */
        String        tipo
) {

    public boolean esRegularizacion() {
        return "REGULARIZACION".equals(tipo);
    }

    /** Título del documento según su tipo. */
    public String titulo() {
        return esRegularizacion() ? "ACTA DE REGULARIZACIÓN DE FALTANTES" : "ACTA DE FALTANTES";
    }

    /** Huella corta para el papel: los primeros 16 caracteres del hash, en grupos de 4. */
    public String huella() {
        if (hash == null || hash.length() < 16) return hash;
        String h = hash.substring(0, 16).toUpperCase();
        return h.substring(0, 4) + "-" + h.substring(4, 8) + "-" + h.substring(8, 12) + "-" + h.substring(12, 16);
    }

    public record Predio(String unidad, String nombre, List<Oficina> oficinas) {
        public int total() {
            return oficinas.stream().mapToInt(o -> o.bienes().size()).sum();
        }
    }

    public record Oficina(Short codOfi, String nombre, List<Bien> bienes) {}

    public record Bien(String codigo, String descripcion) {}
}
