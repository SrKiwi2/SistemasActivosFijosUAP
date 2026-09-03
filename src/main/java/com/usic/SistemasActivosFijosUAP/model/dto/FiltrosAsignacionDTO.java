package com.usic.SistemasActivosFijosUAP.model.dto;

/**
 * Filtros del listado de Movimientos / Asignaciones.
 * <p>
 * Van juntos en un record y no como ocho parámetros sueltos porque los usan tres
 * lugares —la página, el conteo total y las tarjetas de resumen— y tienen que ser
 * exactamente los mismos en los tres: si el resumen contara sobre un conjunto distinto
 * al que se lista, las tarjetas dirían una cosa y la tabla otra.
 *
 * @param tipo           NUEVA / REASIGNACION / DEVOLUCION; vacío = todos
 * @param estado         ACTIVA / ANULADA / DEVUELTA; vacío = todos
 * @param buscar         texto libre: número de acta, documento, responsable o código de activo
 * @param desde          fecha ISO (yyyy-MM-dd) desde la que se listan actas
 * @param hasta          fecha ISO (yyyy-MM-dd) hasta la que se listan actas
 * @param sincronizacion {@link #SUBIDAS} (por defecto), {@link #COMPLETAS}, {@link #PARCIALES} o {@link #TODAS}
 * @param gestion        año de la asignación
 * @param idResponsable  responsable destino del acta
 * @param soloConError   solo actas con algún bien que el VSIAF rechazó
 * @param oficina        texto libre sobre el nombre de la oficina destino
 * @param idUsuarioRegistro usuario que registró el acta (auditoría, no responsable del bien)
 * @param comprobante    true = solo con comprobante, false = solo sin comprobante, null = todas
 */
public record FiltrosAsignacionDTO(
        String tipo,
        String estado,
        String buscar,
        String desde,
        String hasta,
        String sincronizacion,
        Integer gestion,
        Long idResponsable,
        boolean soloConError,
        String oficina,
        Long idUsuarioRegistro,
        Boolean comprobante) {

    /** Al menos un bien ya está en el VSIAF. Es lo que se muestra por defecto. */
    public static final String SUBIDAS = "SUBIDAS";
    /** Todos los bienes llegaron al VSIAF. */
    public static final String COMPLETAS = "COMPLETAS";
    /** Algunos llegaron y otros siguen pendientes. */
    public static final String PARCIALES = "PARCIALES";
    /** Sin filtrar: incluye las actas que todavía no subió nadie. */
    public static final String TODAS = "TODAS";

    /**
     * Normaliza lo que llega del formulario: cadenas vacías a null y el modo de
     * sincronización a uno de los cuatro valores válidos.
     * <p>
     * El valor por defecto es {@link #SUBIDAS} porque este módulo lista movimientos ya
     * registrados en el VSIAF; las actas que nadie subió todavía se atienden en la
     * bandeja de Pendientes. Las parciales sí aparecen: si se ocultaran, un acta a la
     * que le faltan dos bienes desaparecería de las dos pantallas.
     */
    public static FiltrosAsignacionDTO normalizar(String tipo, String estado, String buscar,
                                                  String desde, String hasta, String sincronizacion,
                                                  Integer gestion, Long idResponsable, Boolean soloConError) {
        return normalizar(tipo, estado, buscar, desde, hasta, sincronizacion,
                gestion, idResponsable, soloConError, null, null, null);
    }

    public static FiltrosAsignacionDTO normalizar(String tipo, String estado, String buscar,
                                                  String desde, String hasta, String sincronizacion,
                                                  Integer gestion, Long idResponsable, Boolean soloConError,
                                                  String oficina, Long idUsuarioRegistro, Boolean comprobante) {
        String sinc = limpiar(sincronizacion);
        if (sinc == null) sinc = SUBIDAS;
        sinc = sinc.toUpperCase();
        if (!SUBIDAS.equals(sinc) && !COMPLETAS.equals(sinc)
                && !PARCIALES.equals(sinc) && !TODAS.equals(sinc)) {
            sinc = SUBIDAS;
        }
        return new FiltrosAsignacionDTO(
                limpiar(tipo), limpiar(estado), limpiar(buscar),
                limpiar(desde), limpiar(hasta), sinc,
                gestion, idResponsable, Boolean.TRUE.equals(soloConError),
                limpiar(oficina), idUsuarioRegistro, comprobante);
    }

    private static String limpiar(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** ¿Hay algo puesto además del modo de sincronización por defecto? */
    public boolean hayFiltrosActivos() {
        return tipo != null || estado != null || buscar != null || desde != null || hasta != null
            || gestion != null || idResponsable != null || soloConError
            || oficina != null || idUsuarioRegistro != null || comprobante != null
            || !SUBIDAS.equals(sincronizacion);
    }
}
