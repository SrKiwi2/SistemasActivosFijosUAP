package com.usic.SistemasActivosFijosUAP.model.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ActivoSyncTracker {
    private final Map<String,Object> s = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        s.clear();
        s.put("running", false);
        s.put("total", 0);
        s.put("procesadas", 0);
        s.put("insertados", 0);
        s.put("actualizados", 0);
        s.put("sinEntidad", 0);
        s.put("sinPredio", 0);
        s.put("sinOficina", 0);
        s.put("sinResponsable", 0);
        s.put("sinGrupo", 0);
        s.put("sinAuxiliar", 0);
        s.put("sinEstado", 0);
        s.put("sinOrgFin", 0);
        // no meter "error" con null
    }

    public void reset(int total) {
        s.clear();
        s.put("running", true);
        s.put("total", total);
        s.put("procesadas", 0);
        s.put("insertados", 0);
        s.put("actualizados", 0);
        s.put("sinEntidad", 0);
        s.put("sinPredio", 0);
        s.put("sinOficina", 0);
        s.put("sinResponsable", 0);
        s.put("sinGrupo", 0);
        s.put("sinAuxiliar", 0);
        s.put("sinEstado", 0);
        s.put("sinOrgFin", 0);
        // si no hay error, mejor borrar la clave
        s.remove("error");
    }

    public void inc(String key) {
        s.computeIfPresent(key, (k, v) -> ((Integer) v) + 1);
    }

    // IMPORTANTE: jamás meter null a una ConcurrentHashMap
    public void set(String key, Object val) {
        if (val == null) {
            s.remove(key); // si te pasan null, elimina la clave
        } else {
            s.put(key, val);
        }
    }

    public Map<String, Object> snapshot() {
        return new LinkedHashMap<>(s);
    }
}
