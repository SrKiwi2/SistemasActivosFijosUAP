package com.usic.SistemasActivosFijosUAP.model.service.personas;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas para decidir si dos registros son la misma persona: C.I. normalizado, nombre
 * normalizado y nombres que no son de una persona. Las usa la sincronización de RESP.DBF
 * y las usará el módulo de personas unificadas (docs/PLAN_PERSONAS_UNIFICADAS.md).
 */
public final class IdentidadPersona {

    /**
     * Parámetro de sistema con C.I. que no identifican a nadie, separados por coma. Vacío
     * por defecto: Kevin confirmó (15-oct-2026) que los C.I. sospechosos de la medición
     * (000000, 123456, 1234567, 1111000, 12349000…) <b>no</b> se tratan como falsos. Solo se
     * agrega uno aquí con confirmación.
     */
    public static final String CLAVE_CI_INVALIDOS = "personas.ci_invalidos";

    public static final Set<String> CI_INVALIDOS_POR_DEFECTO = Set.of();

    /**
     * Marcas del VSIAF en NOMRESP que no son una persona (D8 del plan): se excluyen de los
     * enlaces automáticos y de la unificación hasta que se corrijan en el VSIAF.
     */
    private static final Set<String> MARCAS_ESPECIALES =
            Set.of("DEVOLUCION", "DEVOLUCIONES", "FALT", "FALTANTE", "FALTANTES", "DEVENGADO", "ACEFALO");

    private static final Pattern NUMERO_CI = Pattern.compile("^(\\d{5,})");

    private IdentidadPersona() {
    }

    /**
     * Número del C.I., sin extensión ni complemento ({@code "1234567 LP"} → {@code 1234567}).
     * Null si no hay al menos 5 dígitos al inicio o si está en la lista del parámetro
     * {@link #CLAVE_CI_INVALIDOS}: en esos casos el registro se trata como «sin C.I.».
     */
    public static String numeroCi(String ci, Set<String> invalidos) {
        if (ci == null) return null;
        // Lo que va después del guion es el complemento (1234567-1A): no es parte del número.
        String base = ci.trim();
        int guion = base.indexOf('-');
        if (guion > 0) base = base.substring(0, guion);
        String limpio = base.replaceAll("[.\\s]", "").toUpperCase(Locale.ROOT);
        Matcher m = NUMERO_CI.matcher(limpio);
        if (!m.find()) return null;
        String numero = m.group(1);
        if (invalidos != null && invalidos.contains(numero)) return null;
        return numero;
    }

    /** C.I. que no identifican: los de por defecto (ninguno) más los del parámetro. */
    public static Set<String> ciInvalidos(String valorParametro) {
        if (valorParametro == null || valorParametro.isBlank()) return CI_INVALIDOS_POR_DEFECTO;
        Set<String> s = new LinkedHashSet<>(CI_INVALIDOS_POR_DEFECTO);
        Arrays.stream(valorParametro.split(","))
                .map(v -> v.trim().replaceAll("[.\\-\\s]", ""))
                .filter(v -> !v.isEmpty())
                .forEach(s::add);
        return s;
    }

    /**
     * Nombre normalizado, <b>igual al que usaba la sync hasta el 15-oct-2026 salvo que
     * conserva la Ñ</b>: mayúsculas, ÁÉÍÓÚ → AEIOU, guiones y puntos como espacio, y se
     * descarta todo lo demás que no sea A-Z, 0-9 o Ñ. No se usa una normalización Unicode
     * general a propósito: las mayúsculas con tilde tecleadas en el VSIAF (código DOS)
     * llegan como otras letras («Í» como «Ö»), y convertirlas en letra base inventaría
     * nombres distintos de los ya guardados.
     */
    public static String normalizarNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) return "";
        String s = nombre.toUpperCase(Locale.ROOT).trim()
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace("-", " ")
                .replace(".", " ");
        s = s.replaceAll("[^A-Z0-9Ñ\\s]", "");
        return s.replaceAll("\\s+", " ").trim();
    }

    /**
     * ¿Son el mismo nombre, salvo cómo quedó la Ñ? Hasta el 15-oct-2026 la sincronización
     * la borraba (Ñ del VSIAF en código DOS, leída como «¥»: {@code ACUÑA} → {@code ACUA}) o
     * la cambiaba por N (Ñ escrita en Windows: {@code ACUÑA} → {@code ACUNA}). Los dos
     * argumentos tienen que venir de {@link #normalizarNombre}.
     */
    public static boolean nombresEquivalentes(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        return a.replace("Ñ", "N").equals(b.replace("Ñ", "N"))
                || sinEnie(a).equals(sinEnie(b));
    }

    /**
     * Versión estricta para enlazar filas <b>nuevas</b>: solo acepta la Ñ borrada
     * ({@code ACUA} = {@code ACUÑA}), no la Ñ cambiada por N, porque PENA / PEÑA o
     * PINA / PIÑA son apellidos distintos de verdad.
     */
    public static boolean nombresEquivalentesEstricto(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        return a.equals(b) || sinEnie(a).equals(sinEnie(b));
    }

    /** La forma vieja de un nombre que perdió la Ñ, sin espacios dobles. */
    private static String sinEnie(String s) {
        return s.replace("Ñ", "").replaceAll("\\s+", " ").trim();
    }

    /** Formas en que la sync vieja pudo haber guardado este nombre (la propia incluida). */
    public static Set<String> formasAnteriores(String nombreNormalizado) {
        Set<String> s = new LinkedHashSet<>();
        if (nombreNormalizado == null || nombreNormalizado.isEmpty()) return s;
        s.add(nombreNormalizado);
        if (nombreNormalizado.indexOf('Ñ') >= 0) {
            s.add(nombreNormalizado.replace("Ñ", "N"));
            s.add(sinEnie(nombreNormalizado));
        }
        return s;
    }

    /** ¿NOMRESP es una marca del VSIAF (DEVOLUCION, FALTANTES, ACEFALO…) y no una persona? */
    public static boolean esNombreEspecial(String nombreNormalizado) {
        if (nombreNormalizado == null || nombreNormalizado.isEmpty()) return true;
        if ("SIN DATOS".equals(nombreNormalizado)) return true;
        for (String palabra : nombreNormalizado.split(" ")) {
            if (MARCAS_ESPECIALES.contains(palabra)) return true;
        }
        return false;
    }

}
