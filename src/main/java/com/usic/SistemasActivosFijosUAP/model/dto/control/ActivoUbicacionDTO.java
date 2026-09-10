package com.usic.SistemasActivosFijosUAP.model.dto.control;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Un activo encontrado por el buscador, con su ubicación completa.
 *
 * <p>A diferencia de {@link ActivoResponsableDTO} —que se usa cuando ya bajaste por el
 * mapa y la ubicación es implícita— acá la ruta viaja en cada fila: el buscador devuelve
 * resultados que pueden venir de cualquier punto del árbol, y lo que el usuario necesita
 * saber es justamente <em>dónde está</em> el bien.
 *
 * <p>Los campos de ubicación son nullables a propósito. Un activo sin oficina, sin
 * responsable o cuyo predio no tiene municipio cargado es una inconsistencia real del
 * dato, y este módulo existe para mostrarla: filtrarlos la escondería.
 */
public record ActivoUbicacionDTO(
        Long      idActivo,
        String    codigo,
        String    descripcion,
        String    estadoActivo,
        String    auxiliar,
        Double    costo,
        // numeric en la base: BigDecimal y no Double, porque el driver de PostgreSQL
        // no convierte numeric a Double y revienta la consulta entera al mapear.
        BigDecimal vidaUtil,
        LocalDate  fechaAdquisicion,

        Long   idMunicipio,
        String municipio,
        Long   idPredio,
        String predio,
        Long   idOficina,
        Short  codOfi,
        String oficina,
        Long   idResponsable,
        String responsable,

        boolean faltanteAbierto,
        boolean observadoAbierto
) {

    /** Ruta legible para mostrar de un vistazo dónde está el bien. */
    public String ubicacion() {
        return String.join(" › ",
                municipio   == null ? "(sin municipio)"   : municipio,
                predio      == null ? "(sin predio)"      : predio,
                oficina     == null ? "(sin oficina)"     : oficina,
                responsable == null ? "(sin responsable)" : responsable);
    }
}
