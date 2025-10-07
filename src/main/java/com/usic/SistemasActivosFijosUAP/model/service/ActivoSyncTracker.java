package com.usic.SistemasActivosFijosUAP.model.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class ActivoSyncTracker {
    private final Map<String,Object> s = new ConcurrentHashMap<>();

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
        s.put("error", null);
    }
    public void inc(String key){ s.computeIfPresent(key, (k,v)-> ((Integer)v)+1 ); }
    public void set(String key, Object val){ s.put(key, val); }
    public Map<String,Object> snapshot(){ return new LinkedHashMap<>(s); }
}
