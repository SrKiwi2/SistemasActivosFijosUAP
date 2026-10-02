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
        String        tipo,
        /** Solo notificaciones (null en las actas): días hábiles para responder. */
        Integer       plazoDias,
        String        ciudad,
        /** Responsable de Activos Fijos que firma, según la configuración de la gestión al emitir. */
        String        firmante,
        /** Unidad organizacional del destinatario (su oficina con más bienes notificados). */
        String        unidad
) {

    public boolean esRegularizacion() {
        return "REGULARIZACION".equals(tipo);
    }

    /** Faltantes emitidos desde el 2-oct-2026: formato de notificación con plazo. */
    public boolean esNotificacion() {
        return plazoDias != null;
    }

    /** Título del documento según su tipo. */
    public String titulo() {
        if (esNotificacion()) return "NOTIFICACIÓN DE ACTIVOS FÍSICOS FALTANTES";
        return esRegularizacion() ? "ACTA DE REGULARIZACIÓN DE FALTANTES" : "ACTA DE FALTANTES";
    }

    /** Como va en el papel: NOT:SCIAF:AF N° 001/2026 (las actas, tal cual). */
    public String numeroImpreso() {
        return com.usic.SistemasActivosFijosUAP.model.entity.ActaFaltante.numeroImpreso(numero);
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

    /**
     * @param descripcion completa, como estaba registrada
     * @param descripcionCorta sin marca/modelo/serie, que van en sus columnas (solo notificaciones)
     */
    public record Bien(String codigo, String descripcion, String descripcionCorta,
                       String marca, String modelo, String serie) {}
}
