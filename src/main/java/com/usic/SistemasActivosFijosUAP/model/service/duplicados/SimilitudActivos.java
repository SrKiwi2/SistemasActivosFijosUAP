package com.usic.SistemasActivosFijosUAP.model.service.duplicados;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.usic.SistemasActivosFijosUAP.model.service.control.DatosTecnicos;

/**
 * Qué tan probable es que dos activos sean <b>el mismo bien</b> registrado dos veces.
 * <p>
 * Solo <b>sugiere</b>: quién afirma que es un duplicado es la persona que vio el bien. Por eso
 * cada sugerencia dice sus motivos ("misma serie", "descripción casi igual"…) y nunca se
 * marca nada solo.
 * <p>
 * El ACTUAL.DBF no tiene marca, modelo ni serie en columnas: van dentro de la descripción
 * (ver {@link DatosTecnicos}). Por eso se compara la descripción normalizada (sin tildes,
 * signos ni palabras de relleno), la serie que aparezca en ella y los datos numéricos que el
 * revalúo pudo copiar igual en los dos registros (costo, costo anterior, fechas).
 */
public final class SimilitudActivos {

    private SimilitudActivos() {}

    /** Lo que se compara de cada activo. */
    public record Datos(String descripcion, Double costo, Double costoAnterior, LocalDate fechaAdquisicion,
                        LocalDate fechaAnterior, Boolean revaluado, Long idAuxiliar, Long idOficina,
                        Long idResponsable) {}

    /** @param puntaje 0 a 100 */
    public record Resultado(int puntaje, double parecidoDescripcion, List<String> motivos) {}

    /** Por debajo de esto no se sugiere. */
    public static final int MINIMO_SUGERENCIA = 35;

    /** Palabras que no distinguen un bien de otro. */
    private static final Set<String> RELLENO = Set.of("DE", "DEL", "LA", "EL", "LOS", "LAS", "CON", "SIN", "PARA",
            "Y", "EN", "A", "AL", "POR", "UN", "UNA", "O", "COLOR", "MARCA", "MODELO", "MOD", "SERIE", "NS", "SN",
            "M", "N", "S", "TIPO", "CM", "MTS", "MT");

    /** Series que no identifican nada (se escribieron cuando no había serie). */
    private static final Set<String> SERIE_VACIA = Set.of("SN", "SS", "NS", "NO", "NA", "ND", "SD", "SINSERIE",
            "SINNUMERO", "NOTIENE", "NOVISIBLE", "NOLEGIBLE", "SINDATO", "SINDATOS", "0", "00", "000", "0000");

    public static Resultado comparar(Datos a, Datos b) {
        List<String> motivos = new ArrayList<>();
        int puntos = 0;

        String serieA = serie(a.descripcion());
        String serieB = serie(b.descripcion());
        boolean mismaSerie = serieA != null && serieA.equals(serieB);
        if (mismaSerie) {
            puntos += 60;
            motivos.add("Mismo N° de serie (" + serieA + ")");
        } else if (serieA != null && serieB != null) {
            // Dos series distintas y legibles: casi seguro son dos bienes.
            puntos -= 40;
            motivos.add("Series distintas (" + serieA + " / " + serieB + ")");
        }

        String na = normalizar(a.descripcion());
        String nb = normalizar(b.descripcion());
        double parecido = parecido(na, nb);
        if (!na.isEmpty() && na.equals(nb)) {
            puntos += 40;
            motivos.add("Misma descripción");
        } else if (parecido >= 0.8) {
            puntos += 30;
            motivos.add("Descripción casi igual (" + Math.round(parecido * 100) + " %)");
        } else if (parecido >= 0.6) {
            puntos += 18;
            motivos.add("Descripción parecida (" + Math.round(parecido * 100) + " %)");
        }

        if (iguales(a.costoAnterior(), b.costoAnterior())) {
            puntos += 15;
            motivos.add("Mismo costo anterior al revalúo");
        } else if (iguales(a.costo(), b.costo())) {
            puntos += 10;
            motivos.add("Mismo costo");
        }
        if (a.fechaAnterior() != null && a.fechaAnterior().equals(b.fechaAnterior())) {
            puntos += 10;
            motivos.add("Misma fecha anterior al revalúo");
        } else if (a.fechaAdquisicion() != null && a.fechaAdquisicion().equals(b.fechaAdquisicion())) {
            puntos += 10;
            motivos.add("Misma fecha de adquisición");
        }
        if (a.revaluado() != null && b.revaluado() != null && !a.revaluado().equals(b.revaluado())) {
            puntos += 5;
            motivos.add("Uno revaluado y el otro no");
        }
        if (a.idAuxiliar() != null && a.idAuxiliar().equals(b.idAuxiliar())) {
            puntos += 5;
            motivos.add("Mismo auxiliar");
        }
        if (a.idResponsable() != null && a.idResponsable().equals(b.idResponsable())) {
            puntos += 5;
            motivos.add("Mismo responsable");
        } else if (a.idOficina() != null && a.idOficina().equals(b.idOficina())) {
            puntos += 3;
            motivos.add("Misma oficina");
        }

        // Sin una descripción parecida ni la misma serie, coincidir en costo o fecha no dice nada.
        if (!mismaSerie && parecido < 0.6) puntos = Math.min(puntos, MINIMO_SUGERENCIA - 1);
        return new Resultado(Math.max(0, Math.min(100, puntos)), parecido, motivos);
    }

    /** Mayúsculas, sin tildes ni signos, un solo espacio. La Ñ se conserva. */
    static String normalizar(String texto) {
        if (texto == null) return "";
        String s = texto.toUpperCase(Locale.ROOT).replace('Ñ', '\u0001');
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('\u0001', 'Ñ');
        s = s.replaceAll("[^A-Z0-9Ñ]+", " ").replaceAll("\\s+", " ").trim();
        return s;
    }

    /** Palabras que distinguen: sin relleno ni letras sueltas. */
    static Set<String> palabras(String normalizado) {
        Set<String> out = new LinkedHashSet<>();
        if (normalizado == null || normalizado.isEmpty()) return out;
        for (String p : normalizado.split(" ")) {
            if (p.length() < 2 || RELLENO.contains(p)) continue;
            out.add(p);
        }
        return out;
    }

    /** Coeficiente de Jaccard entre las palabras de las dos descripciones (0 a 1). */
    static double parecido(String normalizadoA, String normalizadoB) {
        Set<String> a = palabras(normalizadoA);
        Set<String> b = palabras(normalizadoB);
        if (a.isEmpty() || b.isEmpty()) return 0;
        long comunes = a.stream().filter(b::contains).count();
        return (double) comunes / (a.size() + b.size() - comunes);
    }

    /** La serie que trae la descripción, solo letras y números; null si no hay o no identifica nada. */
    public static String serieDe(String descripcion) {
        return serie(descripcion);
    }

    /** La serie de la descripción, solo letras y números; null si no hay o no identifica nada. */
    static String serie(String descripcion) {
        String s = DatosTecnicos.de(descripcion).serie();
        if (s == null) return null;
        String limpia = normalizar(s).replace(" ", "");
        if (limpia.length() < 4 || SERIE_VACIA.contains(limpia)) return null;
        return limpia;
    }

    private static boolean iguales(Double x, Double y) {
        return x != null && y != null && x > 0 && Objects.equals(Math.round(x * 100), Math.round(y * 100));
    }
}
