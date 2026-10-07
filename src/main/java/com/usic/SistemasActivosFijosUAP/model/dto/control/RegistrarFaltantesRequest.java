package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.time.LocalDate;
import java.util.List;

/**
 * Registro de faltantes de una persona: los bienes elegidos (de cualquiera de sus oficinas)
 * y el documento que lo respalda. Genera una sola notificación de faltantes.
 */
public record RegistrarFaltantesRequest(
        /** A nombre de quién sale la notificación (y quién queda en la oficina de faltantes). */
        Long       idPersona,
        List<Long> idsActivos,
        String     documentoRespaldo,
        LocalDate  fechaDocumento,
        String     observacion,
        /** Días hábiles que tiene la persona para informar sobre los bienes (obligatorio). */
        Integer    plazoDias,
        /**
         * Otros registros de {@code persona} que son la misma persona (la sincronización con el
         * VSIAF a veces la parte en dos: uno con C.I. y otro sin). Sus bienes entran en la misma
         * notificación. Arreglo temporal hasta que se unifiquen las personas en la base.
         */
        List<Long> idsPersonasVinculadas,
        /** A quién de la oficina de faltantes de cada predio van los bienes; ver {@link DestinoCustodia}. */
        List<DestinoCustodia> destinos,
        /**
         * Nombre del responsable para el documento (permite agregar título profesional:
         * Ing., Lic., MsC., etc.). Si es null o vacío, se usa el nombre de la persona en BD.
         * <b>No actualiza la base de datos</b>, solo se refleja en el PDF/acta generado.
         */
        String     personaNombreOverride,
        /**
         * Cargo del responsable para el documento. Si es null o vacío, se usa el cargo
         * calculado de los bienes notificados. <b>No actualiza la base de datos</b>.
         */
        String     personaCargoOverride,
        /**
         * Unidad/oficina del responsable para el documento (p.ej. unidad funcional).
         * Si es null o vacío, se usa la unidad calculada (oficina con más bienes).
         * <b>No actualiza la base de datos</b>.
         */
        String     personaUnidadOverride
) {

    /**
     * Destino elegido en la oficina de faltantes de un predio.
     *
     * @param idResponsableCustodia responsable que ya está en esa oficina y recibe los bienes
     * @param crearNuevo            sin {@code idResponsableCustodia}: dar de alta a la persona
     *                              aunque allí haya alguien con su mismo nombre (homónimo real)
     */
    public record DestinoCustodia(Long idPredio, Long idResponsableCustodia, Boolean crearNuevo) {}
}
