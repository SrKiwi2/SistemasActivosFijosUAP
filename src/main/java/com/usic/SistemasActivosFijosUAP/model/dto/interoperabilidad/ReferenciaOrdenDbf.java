package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

/**
 * A qué apunta una orden encolada para el VSIAF.
 * <p>
 * Sin esto la fila de {@code dbf_cola_orden} no se puede conectar con nada del dominio:
 * cuando el worker rechaza un UPDATE, hay que poder decir <em>qué activo</em> quedó
 * desincronizado, no solo que falló una orden sobre ACTUAL.
 *
 * @param idActivo   activo afectado; null cuando la orden es de AUXILIAR, OFICINA o RESP
 * @param idRegistro id en el SCIAF de la oficina / responsable / auxiliar de la orden;
 *                   null en las de ACTUAL (esas usan {@code idActivo}) y en las viejas
 * @param referencia código visible: el del activo, o el del auxiliar / oficina / responsable
 * @param usuario    quién disparó la operación
 */
public record ReferenciaOrdenDbf(Long idActivo, Long idRegistro, String referencia, String usuario) {

    /** Orden sobre un activo: es la única que permite marcar el bien como desincronizado. */
    public static ReferenciaOrdenDbf deActivo(Long idActivo, String codigo, String usuario) {
        return new ReferenciaOrdenDbf(idActivo, null, codigo, usuario);
    }

    /** Orden sobre una tabla de apoyo (auxiliar, oficina, responsable) sin registro conocido. */
    public static ReferenciaOrdenDbf deApoyo(String referencia, String usuario) {
        return new ReferenciaOrdenDbf(null, null, referencia, usuario);
    }

    /**
     * Orden sobre una tabla de apoyo, ligada al registro del SCIAF que la originó. Es lo
     * que permite mostrar en los módulos de Oficinas y Responsables si el alta o la
     * edición ya llegó al VSIAF, sigue en la cola o el worker la rechazó.
     */
    public static ReferenciaOrdenDbf deApoyo(Long idRegistro, String referencia, String usuario) {
        return new ReferenciaOrdenDbf(null, idRegistro, referencia, usuario);
    }
}
