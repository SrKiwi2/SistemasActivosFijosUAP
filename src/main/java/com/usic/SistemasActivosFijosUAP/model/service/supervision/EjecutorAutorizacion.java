package com.usic.SistemasActivosFijosUAP.model.service.supervision;

import java.util.Map;
import java.util.Set;

import com.usic.SistemasActivosFijosUAP.model.entity.Usuario;

/**
 * Quien sabe ejecutar un tipo de solicitud de autorización una vez aprobada. Lo
 * implementan los servicios de gestión de cada módulo (oficinas, responsables), que son
 * los mismos que usan los administradores para aplicar el cambio directo.
 */
public interface EjecutorAutorizacion {

    /** Tipos de solicitud que atiende (p. ej. OFICINA_MODIFICAR). */
    Set<String> tipos();

    /**
     * Aplica la operación con los datos guardados en la solicitud.
     *
     * @param autor quien la pidió: queda como autor del cambio en el VSIAF
     * @return mensaje del resultado para el solicitante y el revisor
     * @throws IllegalArgumentException / IllegalStateException si ya no se puede aplicar
     */
    String ejecutar(String tipo, Map<String, Object> datos, Usuario autor);
}
