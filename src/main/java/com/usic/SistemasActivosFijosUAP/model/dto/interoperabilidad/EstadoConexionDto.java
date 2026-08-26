package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

/**
 * Foto del estado de una conexión de la que depende el sistema
 * (montajes CIFS del VSIAF y base de datos).
 *
 * <p>Lo consume el indicador del topbar vía {@code /api/estado/conexiones}
 * y el push SSE {@code estado-conexiones}.</p>
 */
public record EstadoConexionDto(
        String  clave,          // dbfwin | transferencias | postgres
        String  nombre,         // etiqueta para el administrador
        String  tipo,           // CIFS | PostgreSQL
        String  estado,         // OK | DEGRADADO | CAIDO
        String  detalle,        // explicación en lenguaje llano
        String  origen,         // //172.16.21.4/dbfs  |  host:puerto/bd
        String  ruta,           // /mnt/dbfwin
        Boolean montado,        // null = no se pudo determinar (no hay /proc/mounts)
        int     archivosDbf,    // cuántos .DBF se ven en la carpeta
        boolean escribible,
        long    latenciaMs,     // cuánto tardó la sonda
        String  ultimoCambio,   // fecha de modificación del DBF centinela
        String  verificadoEn,   // ISO-8601 de esta verificación
        String  error           // mensaje técnico, null si todo bien
) {
    public static final String OK        = "OK";
    public static final String DEGRADADO = "DEGRADADO";
    public static final String CAIDO     = "CAIDO";

    /**
     * Hubo novedad respecto de la verificacion anterior. La primera vez
     * (previo == null) solo cuenta como novedad si algo esta mal: asi el
     * sistema avisa aunque arranque con el montaje ya caido.
     */
    public boolean huboNovedad(EstadoConexionDto previo) {
        if (previo == null) return !OK.equals(this.estado);
        return !previo.estado.equals(this.estado);
    }
}
