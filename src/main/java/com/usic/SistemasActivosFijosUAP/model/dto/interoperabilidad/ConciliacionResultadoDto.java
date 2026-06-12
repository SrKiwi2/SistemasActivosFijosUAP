package com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Resultado de una conciliación entre la BD PostgreSQL y el DBF del VSIAF.
 * Agrupa las dos direcciones de divergencia y sus conteos para el tablero.
 */
@Getter
@Builder
public class ConciliacionResultadoDto {

    /** Activos en PostgreSQL cuyo código NO está en el DBF del VSIAF. */
    private final List<DivergenciaActivoDto> enBdNoEnVsiaf;

    /** Códigos en el DBF del VSIAF que NO están en la BD PostgreSQL. */
    private final List<DivergenciaActivoDto> enVsiafNoEnBd;

    // ── Conteos para las tarjetas del tablero ──────────────────────────────
    private final long totalBd;            // total de activos leídos en BD (sin ELIMINADO)
    private final long totalVsiaf;         // total de códigos leídos del DBF
    private final long pendientesSync;     // en BD, estado PENDIENTE (esperando aprobación)
    private final long anomaliasActivo;    // en BD, estado ACTIVO sin respaldo en DBF (revisar)
    private final long otroEstado;         // en BD, otros estados sin reflejo en DBF
    private final long soloVsiaf;          // en DBF pero ausentes en BD

    private final LocalDateTime generadoEn;
    private final long duracionMs;
}
