package com.usic.SistemasActivosFijosUAP.model.dto.control;

/**
 * Una persona en el buscador del registro de faltantes. Una misma persona es responsable
 * en varias oficinas (y predios): acá se la ve una sola vez, con el total de sus bienes.
 */
public record PersonaFaltanteDTO(
        Long   idPersona,
        String nombre,
        String ci,
        long   bienes,
        long   oficinas,
        long   predios,
        /** Faltantes sin aclarar (abiertos o en custodia). */
        long   faltantesPendientes,
        /** Cargo que aporta más bienes (una persona tiene uno por oficina). */
        String cargoPrincipal,
        /** Oficina que aporta más bienes. */
        String oficinaPrincipal
) {}
