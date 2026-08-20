package com.usic.SistemasActivosFijosUAP.model.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.usic.SistemasActivosFijosUAP.model.entity.Activo;
import com.usic.SistemasActivosFijosUAP.model.entity.AsignacionActivo;
import com.usic.SistemasActivosFijosUAP.model.entity.DetalleAsignacionActivo;

import lombok.Data;

@Data
public class AsignacionPendienteDTO {
    private AsignacionActivo asignacion;
    private String encryptedAsignacionId;
    private List<ActivoPendienteItemDTO> items;
    private int totalActivos;
    private long totalSincronizados;
    private long totalPendientes;

    /** Activos PENDIENTES que aún tienen datos obligatorios incompletos. */
    public long getTotalIncompletos() {
        return items == null ? 0 : items.stream().filter(i -> !i.isCompleto()).count();
    }

    /** Activos PENDIENTES completos y listos para subir al VSIAF. */
    public long getTotalListosParaSubir() {
        return items == null ? 0 : items.stream().filter(ActivoPendienteItemDTO::isCompleto).count();
    }

    // ── Costos ────────────────────────────────────────────────────────────
    // Se calculan sobre TODOS los activos de la asignación (pendientes y ya
    // subidos), no solo sobre `items`, que contiene únicamente los PENDIENTES.
    // Los cancelados quedan fuera, igual que en totalActivos.

    private Stream<Activo> activosVigentes() {
        if (asignacion == null || asignacion.getDetalles() == null) return Stream.empty();
        return asignacion.getDetalles().stream()
                .map(DetalleAsignacionActivo::getActivo)
                .filter(Objects::nonNull)
                .filter(a -> !"CANCELADO".equalsIgnoreCase(a.getEstado()));
    }

    private static boolean tieneCosto(Activo a) {
        return a.getCosto() != null && a.getCosto() > 0;
    }

    /** Suma de los costos cargados. Los activos sin costo no suman (no son cero: faltan). */
    public BigDecimal getCostoTotal() {
        return activosVigentes()
                .filter(AsignacionPendienteDTO::tieneCosto)
                .map(a -> BigDecimal.valueOf(a.getCosto()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Códigos de los activos a los que todavía les falta el costo. */
    public List<String> getCodigosSinCosto() {
        return activosVigentes()
                .filter(a -> !tieneCosto(a))
                .map(a -> a.getCodigo() == null ? "(sin código)" : a.getCodigo())
                .toList();
    }

    public int getTotalSinCosto() {
        return getCodigosSinCosto().size();
    }
}
