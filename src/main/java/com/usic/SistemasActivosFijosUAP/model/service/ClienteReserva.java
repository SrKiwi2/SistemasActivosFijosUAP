package com.usic.SistemasActivosFijosUAP.model.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Quién pide una reserva de correlativo (adicionales, registro de activos, donaciones).
 *
 * @param version  versión del formulario. Las pantallas anteriores no la envían y renovaban
 *                 sin fin: se les pide recargar y su reserva vence.
 * @param pagina   la pestaña del navegador (se conserva al recargar con F5).
 * @param carga    el número de carga de la página dentro de esa pestaña.
 * @param vigentes las reservas que el formulario sigue usando: las demás de cargas anteriores
 *                 de esta pestaña quedaron huérfanas (una renovación tardía de la página vieja
 *                 podía volver a crearlas después de liberarlas).
 */
public record ClienteReserva(Integer version, String pagina, Integer carga, List<String> vigentes) {

    /** Llamadas internas (sin pantalla): no se controla la pestaña. */
    public static final ClienteReserva INTERNO = new ClienteReserva(2, null, null, List.of());

    /** Lee {@code version, pagina, carga, vigentes} del cuerpo que envía la pantalla. */
    public static ClienteReserva de(Map<String, ?> cuerpo) {
        if (cuerpo == null) return new ClienteReserva(null, null, null, List.of());
        String pagina = cuerpo.get("pagina") == null ? null : String.valueOf(cuerpo.get("pagina")).trim();
        if (pagina != null && (pagina.isEmpty() || pagina.length() > 40)) pagina = null;
        List<String> vigentes = new ArrayList<>();
        if (cuerpo.get("vigentes") instanceof List<?> l)
            l.stream().filter(t -> t != null && String.valueOf(t).length() <= 40).limit(40).forEach(t -> vigentes.add(String.valueOf(t)));
        return new ClienteReserva(entero(cuerpo.get("version")), pagina, entero(cuerpo.get("carga")), vigentes);
    }

    private static Integer entero(Object valor) {
        if (valor == null) return null;
        try { return Integer.valueOf(String.valueOf(valor)); } catch (NumberFormatException e) { return null; }
    }

    /** Pantalla de una versión anterior: se le pide recargar. */
    public void exigirVersion() {
        if (version == null)
            throw new IllegalStateException("Esta pantalla es de una versión anterior del sistema. Recárguela (F5) para seguir registrando.");
    }

    public boolean conPagina() { return pagina != null && carga != null; }

    /** El token propio más los demás que el formulario sigue usando. */
    public List<String> vigentesCon(String token) {
        List<String> todos = new ArrayList<>(vigentes == null ? List.of() : vigentes);
        todos.add(token);
        return todos;
    }

    /** La página que pidió la reserva ya fue reemplazada (F5) en la misma pestaña. */
    public static class ReservaObsoletaException extends IllegalStateException {
        public ReservaObsoletaException() {
            super("Esta pantalla se volvió a cargar en esta misma pestaña: este formulario ya no reserva códigos.");
        }
    }
}
