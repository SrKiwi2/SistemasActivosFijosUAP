package com.usic.SistemasActivosFijosUAP.model.service.importacion;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;

import lombok.Data;

@Service
public class ImportProgressService {
    @Data
    public static class Snapshot {
        private int total;
        private int leidas;
        private int insertados;
        private int actualizados;
        private int errores;
        private boolean terminado;
        private String mensaje;
        private List<String> muestrasErrores = new ArrayList<>();
    }

    private final ConcurrentMap<String, Snapshot> map = new ConcurrentHashMap<>();

    public String start(int total) {
        String id = UUID.randomUUID().toString();
        Snapshot s = new Snapshot();
        s.total = total;
        map.put(id, s);
        return id;
    }

    public void inc(String id, Consumer<Snapshot> c) {
        Snapshot s = map.get(id);
        if (s != null) c.accept(s);
    }

    public Snapshot get(String id) { return map.get(id); }

    public void finish(String id, String mensaje) {
        inc(id, s -> { s.terminado = true; s.mensaje = mensaje; });
    }
}
