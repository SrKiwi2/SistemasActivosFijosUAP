package com.usic.SistemasActivosFijosUAP.model.service.movil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.usic.SistemasActivosFijosUAP.model.dto.movil.PayloadQr;

/**
 * Interpreta el contenido de las etiquetas QR de activos y normaliza los
 * códigos escritos a mano.
 *
 * <p><b>Por qué vive en el servidor y no en la app:</b> si mañana cambia el
 * formato de etiqueta, se actualiza aquí y todos los teléfonos se enteran al
 * instante. Si el parseo definitivo estuviera dentro del APK, habría que
 * recompilarlo y volver a repartirlo. La app hace una lectura rápida en local
 * solo para dar respuesta inmediata; la que vale es esta.
 *
 * <p>Anatomía del código ({@code ActivosController.construirCodigo}):
 * <pre>
 *   01  -  04  -  02  -  03609
 *   │      │      │      └── correlativo
 *   │      │      └───────── grupo contable (codDbf, 2 dígitos)
 *   │      └──────────────── predio  (Predio.codigo)
 *   └─────────────────────── municipio (Municipio.codigo)
 * </pre>
 */
@Service
public class ParserQrActivoService {

    /**
     * Código de activo, con prefijo de entidad opcional.
     *
     * <p>El prefijo es opcional y los cuatro segmentos son obligatorios, así que
     * {@code 01-04-02-03609} no se confunde con un código prefijado: para que
     * {@code 01} fuera prefijo harían falta cuatro segmentos más y solo hay tres.
     */
    private static final Pattern CODIGO = Pattern.compile(
            "(?:(\\d{2,4})-)?(\\d{2})-(\\d{2})-(\\d{2})-(\\d{3,6})");

    private static final Pattern CODIGO_COMPLETO = Pattern.compile("^\\s*" + CODIGO.pattern() + "\\s*$");

    private static final int CAMPO_SIGLA       = 0;
    private static final int CAMPO_MUNICIPIO   = 1;
    private static final int CAMPO_PREDIO      = 2;
    private static final int CAMPO_GRUPO       = 3;
    private static final int CAMPO_DESCRIPCION = 5;

    // =========================================================================
    //  Etiquetas escaneadas
    // =========================================================================

    /**
     * Interpreta lo que devolvió el lector de QR.
     *
     * <p>Tolera tres formas, de la más rica a la más pobre:
     * <ol>
     *   <li>los 6 campos separados por {@code |} (etiqueta actual),</li>
     *   <li>una URL o un texto cualquiera que contenga el código,</li>
     *   <li>el código pelado.</li>
     * </ol>
     */
    public PayloadQr parsear(String crudo) {
        if (crudo == null || crudo.isBlank()) {
            return PayloadQr.ilegible(crudo);
        }

        String texto = crudo.trim();
        List<String> campos = trocear(texto);

        // 1) Buscar el código entre los campos: un campo que sea ÍNTEGRAMENTE un
        //    código es una señal mucho más fiable que rebuscar en todo el texto,
        //    donde la descripción («D:0,60*0,46*0,69») podría confundir.
        Matcher m = null;
        for (String campo : campos) {
            Matcher candidato = CODIGO_COMPLETO.matcher(campo);
            if (candidato.matches()) {
                m = candidato;
                break;
            }
        }

        // 2) Si no, buscar el primer código que aparezca en el texto completo.
        if (m == null) {
            Matcher busqueda = CODIGO.matcher(texto);
            if (busqueda.find()) {
                m = busqueda;
            }
        }

        if (m == null) {
            // Sin código no hay nada que consultar, pero se conservan los textos
            // para poder mostrarle al usuario qué leyó exactamente.
            return new PayloadQr(crudo, campos,
                    campo(campos, CAMPO_SIGLA), campo(campos, CAMPO_MUNICIPIO),
                    campo(campos, CAMPO_PREDIO), campo(campos, CAMPO_GRUPO),
                    campo(campos, CAMPO_DESCRIPCION),
                    null, null, null, null, null, null, null, false, false);
        }

        return construir(crudo, campos, m, false);
    }

    // =========================================================================
    //  Entrada manual
    // =========================================================================

    /**
     * Normaliza un código tecleado. Se acepta a propósito casi cualquier forma:
     * en campo se escribe con prisa, con guantes y copiando de una etiqueta
     * gastada.
     *
     * <pre>
     *   148-01-04-02-03609  → 01-04-02-03609   (prefijo de entidad retirado)
     *   01-04-02-03609      → 01-04-02-03609
     *   14801040203609      → 01-04-02-03609   (14 dígitos, con prefijo)
     *   01040203609         → 01-04-02-03609   (11 dígitos, sin prefijo)
     *   3609                → solo correlativo → hay que buscar candidatos
     * </pre>
     */
    public PayloadQr parsearEntradaManual(String texto) {
        if (texto == null || texto.isBlank()) {
            return PayloadQr.ilegible(texto);
        }

        String limpio = texto.trim().toUpperCase().replaceAll("\\s+", "");

        Matcher m = CODIGO_COMPLETO.matcher(limpio);
        if (m.matches()) {
            return construir(texto, List.of(), m, false);
        }

        // Solo dígitos: reponer los guiones por posición.
        String digitos = limpio.replaceAll("[^0-9]", "");
        if (digitos.length() == limpio.length() && !digitos.isEmpty()) {

            if (digitos.length() >= 11) {
                // Los 11 últimos son el código; lo anterior es el prefijo de entidad.
                String cuerpo  = digitos.substring(digitos.length() - 11);
                String prefijo = digitos.substring(0, digitos.length() - 11);
                String conGuiones = cuerpo.substring(0, 2) + "-" + cuerpo.substring(2, 4) + "-"
                                  + cuerpo.substring(4, 6) + "-" + cuerpo.substring(6);
                String candidato = prefijo.isEmpty() ? conGuiones : prefijo + "-" + conGuiones;

                Matcher m2 = CODIGO_COMPLETO.matcher(candidato);
                if (m2.matches()) {
                    return construir(texto, List.of(), m2, false);
                }
            }

            if (digitos.length() <= 6) {
                // Solo el correlativo: no alcanza para un código, hay que buscar.
                String correlativo = String.format("%05d", Long.parseLong(digitos));
                return new PayloadQr(texto, List.of(), null, null, null, null, null,
                        null, null, null, null, null, null, correlativo, true, true);
            }
        }

        // Podría venir pegado a otro texto; último intento.
        Matcher suelto = CODIGO.matcher(limpio);
        if (suelto.find()) {
            return construir(texto, List.of(), suelto, false);
        }

        return PayloadQr.ilegible(texto);
    }

    // =========================================================================
    //  Auxiliares
    // =========================================================================

    private PayloadQr construir(String crudo, List<String> campos, Matcher m, boolean soloCorrelativo) {
        String prefijo     = m.group(1);
        String codMun      = m.group(2);
        String codPred     = m.group(3);
        String codGrupo    = m.group(4);
        String correlativo = m.group(5);

        String codigo = codMun + "-" + codPred + "-" + codGrupo + "-" + correlativo;
        String visual = (prefijo != null) ? prefijo + "-" + codigo : codigo;

        return new PayloadQr(crudo, campos,
                campo(campos, CAMPO_SIGLA),
                campo(campos, CAMPO_MUNICIPIO),
                campo(campos, CAMPO_PREDIO),
                campo(campos, CAMPO_GRUPO),
                campo(campos, CAMPO_DESCRIPCION),
                visual, prefijo, codigo,
                codMun, codPred, codGrupo, correlativo,
                true, soloCorrelativo);
    }

    private List<String> trocear(String texto) {
        if (!texto.contains("|")) return List.of();
        List<String> campos = new ArrayList<>();
        for (String parte : texto.split("\\|", -1)) {
            campos.add(parte.trim());
        }
        return campos;
    }

    private String campo(List<String> campos, int indice) {
        if (indice < 0 || indice >= campos.size()) return null;
        String v = campos.get(indice);
        return (v == null || v.isBlank()) ? null : v;
    }

    /** Código visual (con prefijo de entidad) para mostrar e imprimir. */
    public String aCodigoVisual(String codigo, String codigoEntidad) {
        if (codigo == null) return null;
        if (codigoEntidad == null || codigoEntidad.isBlank()) return codigo;
        return codigoEntidad + "-" + codigo;
    }
}
