package com.usic.SistemasActivosFijosUAP.model.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.IDbfColaOrdenDao;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.ReferenciaOrdenDbf;
import com.usic.SistemasActivosFijosUAP.model.entity.DbfColaOrden;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deja constancia en la base de cada orden que se emite hacia el VSIAF.
 * <p>
 * Va en una clase aparte de {@link DbfColaService} por el {@code REQUIRES_NEW}: el
 * archivo en {@code _cola/} ya está escrito y el worker lo va a procesar aunque la
 * transacción que lo originó termine cayéndose. Si la fila viviera en esa transacción,
 * un rollback la borraría y quedaría una orden viajando al VSIAF sin registro de que
 * existió — justo el punto ciego que esta tabla vino a cerrar.
 * <p>
 * No toca la tabla {@code activo}. Marcar ahí el "en cola" desde acá significaría
 * actualizar, en una transacción nueva, una fila que la transacción llamadora ya tiene
 * bloqueada: se traba esperándose a sí misma. El estado del activo lo pone quien lo
 * está guardando, y lo cierra {@code ColaConfirmacionScheduler}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbfColaRegistroService {

    private final IDbfColaOrdenDao dao;

    /**
     * Registra una orden recién encolada.
     * <p>
     * Nunca propaga: si falla el registro, el archivo ya está escrito y la orden va a
     * llegar igual al VSIAF. Perder la trazabilidad es malo; abortar la operación del
     * usuario por no haber podido anotarla es peor.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(String archivo, String tabla, String operacion,
                          Map<String, String> clave, ReferenciaOrdenDbf ref) {
        try {
            DbfColaOrden orden = new DbfColaOrden();
            orden.setArchivo(archivo);
            orden.setTabla(tabla);
            orden.setOperacion(operacion);
            orden.setClave(describirClave(clave));
            orden.setEstado(DbfColaOrden.ENCOLADA);
            if (ref != null) {
                orden.setIdActivo(ref.idActivo());
                orden.setReferencia(recortar(ref.referencia(), 120));
                orden.setUsuario(recortar(ref.usuario(), 60));
            }
            dao.save(orden);
        } catch (Exception e) {
            log.error("[COLA] No se pudo registrar la orden {} ({} {}): {}",
                    archivo, operacion, tabla, e.getMessage());
        }
    }

    /** "CODIGO=01-02-03-00123", para leer de un vistazo a qué fila del DBF apuntaba. */
    private String describirClave(Map<String, String> clave) {
        if (clave == null || clave.isEmpty()) return null;
        return recortar(clave.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(" ")), 300);
    }

    private String recortar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
