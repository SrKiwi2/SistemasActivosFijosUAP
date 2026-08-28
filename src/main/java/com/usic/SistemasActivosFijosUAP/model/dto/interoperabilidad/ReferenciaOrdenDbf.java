package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

/**
 * A qué apunta una orden encolada para el VSIAF.
 * <p>
 * Sin esto la fila de {@code dbf_cola_orden} no se puede conectar con nada del dominio:
 * cuando el worker rechaza un UPDATE, hay que poder decir <em>qué activo</em> quedó
 * desincronizado, no solo que falló una orden sobre ACTUAL.
 *
 * @param idActivo   activo afectado; null cuando la orden es de AUXILIAR, OFICINA o RESP
 * @param referencia código visible: el del activo, o el del auxiliar / oficina / responsable
 * @param usuario    quién disparó la operación
 */
public record ReferenciaOrdenDbf(Long idActivo, String referencia, String usuario) {

    /** Orden sobre un activo: es la única que permite marcar el bien como desincronizado. */
    public static ReferenciaOrdenDbf deActivo(Long idActivo, String codigo, String usuario) {
        return new ReferenciaOrdenDbf(idActivo, codigo, usuario);
    }

    /** Orden sobre una tabla de apoyo (auxiliar, oficina, responsable). */
    public static ReferenciaOrdenDbf deApoyo(String referencia, String usuario) {
        return new ReferenciaOrdenDbf(null, referencia, usuario);
    }
}
