package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

public record FileState (long lastModifiedMs, long sizeBytes) {
    public boolean hasChangedFrom(FileState other) {
        if (other == null) return false;
        return this.lastModifiedMs != other.lastModifiedMs
            || this.sizeBytes != other.sizeBytes;
    }
}
