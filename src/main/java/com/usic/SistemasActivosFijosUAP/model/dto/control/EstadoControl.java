package com.usic.SistemasActivosFijosUAP.model.dto.control;

/**
 * Color de un tile del mapa: qué tan controlada está una ubicación.
 *
 * <p>Es un eje distinto de la vigencia del responsable. Mezclarlos volvería el
 * mapa ilegible, así que la vigencia viaja aparte como bandera del tile.
 */
public enum EstadoControl {

    /** Nunca se levantó. Gris. */
    SIN_LEVANTAR,
    /** Hay un levantamiento abierto ahora mismo. Ámbar. */
    EN_CURSO,
    /** Se levantó y no quedaron faltantes abiertos. Verde. */
    CONTROLADO,
    /** Tiene al menos un faltante sin resolver. Rojo. */
    CON_FALTANTES;

    /**
     * Un faltante abierto manda sobre todo lo demás: aunque haya un recorrido en
     * curso, lo que el mapa tiene que gritar es que falta algo sin resolver.
     */
    public static EstadoControl de(long faltantesAbiertos, long enCurso, long levantamientos) {
        if (faltantesAbiertos > 0) return CON_FALTANTES;
        if (enCurso > 0)           return EN_CURSO;
        if (levantamientos > 0)    return CONTROLADO;
        return SIN_LEVANTAR;
    }
}
