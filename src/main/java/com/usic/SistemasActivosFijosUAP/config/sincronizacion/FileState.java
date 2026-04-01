package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

public record FileState (long lastModifiedMs, long sizeBytes) {
    
    /**
     * Solo es cambio real si:
     * - El tamaño cambió (INSERT / DELETE en DBF), O
     * - El lastModified cambió Y han pasado al menos MIN_DELTA_MS desde el anterior
     *   (evita falsos positivos por accesos de lectura en CIFS)
     */
    private static final long MIN_DELTA_MS = 5_000; // 5 segundos de diferencia mínima

    public boolean hasChangedFrom(FileState other) {
        if (other == null) return false;

        boolean sizeChanged = this.sizeBytes != other.sizeBytes;
        boolean timeChanged = Math.abs(this.lastModifiedMs - other.lastModifiedMs) > MIN_DELTA_MS;

        return sizeChanged || timeChanged;
    }
}
