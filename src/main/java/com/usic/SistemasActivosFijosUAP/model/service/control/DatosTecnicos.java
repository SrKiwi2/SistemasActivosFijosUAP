package com.usic.SistemasActivosFijosUAP.model.service.control;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Marca, modelo y N° de serie de un activo, sacados de su descripción.
 * <p>
 * El activo no tiene esos campos: el registro los agrega al final de la descripción
 * ({@code ... COLOR NEGRO M:EPSON, MOD:L3210, N/S:XAGC410432}) y en el VSIAF se escribieron
 * a mano de muchas formas ({@code MARCA HP}, {@code MARCA: DELL}, {@code MOD.}, {@code S:},
 * {@code SERIE:}...). Se toma la primera aparición de cada uno; el valor llega hasta la
 * coma siguiente (", ": la de "3,70GHZ" no corta) o hasta la etiqueta siguiente. Si el
 * valor es demasiado largo para ser una marca, un modelo o una serie (texto libre que sigue
 * a la etiqueta), no se separa. Lo que no se encuentra queda en null y su texto sigue en la
 * descripción: nunca se pierde texto, solo se reparte en columnas.
 */
public record DatosTecnicos(String descripcion, String marca, String modelo, String serie) {

    private enum Tipo {
        MARCA(3), MODELO(4), SERIE(3);
        /** Más palabras que esto no es un dato técnico: es texto libre después de la etiqueta. */
        final int maxPalabras;
        Tipo(int maxPalabras) { this.maxPalabras = maxPalabras; }
    }

    private static final Pattern SEPARADOR = Pattern.compile(",(?=\\s|$)");

    // Antes de cada etiqueta no puede haber letra, número ni "/": así "RAM:" o "ROM:" no son
    // "M:", y "N/S:" no se lee dos veces como "S:".
    private static final String ANTES = "(?<![A-Z0-9/ÑÁÉÍÓÚ])";
    private static final Pattern ETIQUETAS = Pattern.compile(
            ANTES + "(?:(?<marca>(?<!SIN\\s{1,3})MARCA\\b\\s*:?|M\\s*:)"
            + "|(?<modelo>MOD(?:ELO)?\\s*[:.]+)"
            + "|(?<serie>N\\s*/\\s*S\\s*:*|S\\s*/\\s*N\\s*:+|N[°º]?\\s*(?:DE\\s+)?SERIE\\s*:*|SERIE\\s*:+|S\\s*:+))",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private record Etiqueta(Tipo tipo, int inicio, int fin) {}

    public static DatosTecnicos de(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) return new DatosTecnicos(descripcion, null, null, null);
        String texto = descripcion.trim();

        List<Etiqueta> etiquetas = new ArrayList<>();
        Matcher m = ETIQUETAS.matcher(texto);
        while (m.find()) {
            Tipo t = m.group("marca") != null ? Tipo.MARCA : m.group("modelo") != null ? Tipo.MODELO : Tipo.SERIE;
            etiquetas.add(new Etiqueta(t, m.start(), m.end()));
        }
        etiquetas.sort(Comparator.comparingInt(Etiqueta::inicio));

        String[] valores = new String[Tipo.values().length];
        boolean[] quitar = new boolean[texto.length()];
        for (int i = 0; i < etiquetas.size(); i++) {
            Etiqueta e = etiquetas.get(i);
            if (valores[e.tipo().ordinal()] != null) continue; // solo la primera de cada tipo
            int limite = i + 1 < etiquetas.size() ? etiquetas.get(i + 1).inicio() : texto.length();
            Matcher sep = SEPARADOR.matcher(texto);
            int coma = sep.find(e.fin()) ? sep.start() : -1;
            int fin = (coma >= 0 && coma < limite) ? coma : limite;
            String valor = limpiar(texto.substring(e.fin(), fin));
            if (e.tipo() == Tipo.SERIE) valor = valor.replaceAll("\\.+$", "");
            if (valor.isEmpty() || valor.split("\\s+").length > e.tipo().maxPalabras) continue;
            // "1.20 M: COLOR CAFE" (metros) no es una marca.
            if (e.tipo() == Tipo.MARCA && valor.toUpperCase().startsWith("COLOR")) continue;
            valores[e.tipo().ordinal()] = valor;
            for (int k = e.inicio(); k < fin; k++) quitar[k] = true;
        }

        if (valores[0] == null && valores[1] == null && valores[2] == null) {
            return new DatosTecnicos(texto, null, null, null);
        }
        StringBuilder resto = new StringBuilder();
        for (int k = 0; k < texto.length(); k++) {
            if (!quitar[k]) resto.append(texto.charAt(k));
        }
        String desc = resto.toString()
                .replaceAll("\\s*,\\s*(,\\s*)+", ", ")   // ", ," que dejan los datos quitados
                .replaceAll("\\s+,", ",")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("^[\\s,.;-]+|[\\s,.;-]+$", "")
                .trim();
        return new DatosTecnicos(desc.isEmpty() ? texto : desc,
                valores[Tipo.MARCA.ordinal()], valores[Tipo.MODELO.ordinal()], valores[Tipo.SERIE.ordinal()]);
    }

    private static String limpiar(String s) {
        return s.replaceAll("^[\\s:.,-]+|[\\s,;]+$", "").trim();
    }
}
