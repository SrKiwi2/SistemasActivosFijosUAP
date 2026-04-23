package com.usic.SistemasActivosFijosUAP.util;

import java.text.Normalizer;

public final class NombreParser {
    private NombreParser() {}

    public record ParteNombre(String nombre, String paterno, String materno) {}

    /**
     * Normaliza y divide el nombre completo.
     * Quita acentos y colapsa espacios para máxima compatibilidad con el DBF legacy.
     */
    public static ParteNombre dividir(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            return new ParteNombre(null, null, null);
        }

        // Normalizar: quitar acentos, mayúsculas, colapsar espacios
        String norm = Normalizer.normalize(nombreCompleto.trim(), Normalizer.Form.NFD)
            .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
            .toUpperCase()
            .replaceAll("\\s+", " ")
            .trim();

        String[] partes = norm.split(" ");

        return switch (partes.length) {
            case 1  -> new ParteNombre(partes[0], null, null);
            case 2  -> new ParteNombre(partes[0], partes[1], null);
            case 3  -> new ParteNombre(partes[0], partes[1], partes[2]);
            // 4+ partes: primer(os) token(s) = nombre, penúltimo = paterno, último = materno
            default -> new ParteNombre(
                String.join(" ", java.util.Arrays.copyOf(partes, partes.length - 2)),
                partes[partes.length - 2],
                partes[partes.length - 1]
            );
        };
    }

    /** Solo normalización sin dividir — útil para comparaciones */
    public static String normalizar(String nombre) {
        if (nombre == null) return "";
        return Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD)
            .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
            .toUpperCase()
            .replaceAll("\\s+", " ")
            .trim();
    }
}
