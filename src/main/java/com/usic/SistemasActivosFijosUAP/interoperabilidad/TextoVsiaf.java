package com.usic.SistemasActivosFijosUAP.interoperabilidad;

/**
 * Arreglos de texto para lo que se lee de los DBF del VSIAF.
 * <p>
 * Los DBF mezclan dos codificaciones: la Ñ tecleada en el VSIAF está en código DOS (byte
 * 0xA5 / 0xA4), que leído en CP1252 sale «¥» / «¤», y lo que escribe el worker del SCIAF
 * (VFPOLEDB) llega bien en CP1252. Por eso no se cambia el código de página de lectura:
 * solo se reponen esas dos letras.
 * <p>
 * Se aplica en cada sincronización (RESP, ACTUAL, OFICINA), <b>no</b> en {@link JavaDbfService}:
 * {@code upsertResponsable} y el alta de oficinas releen el DBF completo con ese lector y lo
 * reescriben, y reparar ahí cambiaría los bytes de filas que nadie tocó.
 */
public final class TextoVsiaf {

    private TextoVsiaf() {
    }

    /** «¥» → «Ñ» y «¤» → «ñ». Null queda null. */
    public static String repararEnieDos(String s) {
        if (s == null) return null;
        if (s.indexOf('¥') < 0 && s.indexOf('¤') < 0) return s;
        return s.replace('¥', 'Ñ').replace('¤', 'ñ');
    }
}
