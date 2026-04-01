package com.usic.SistemasActivosFijosUAP.componet;

import java.time.LocalDateTime;

import org.springframework.context.ApplicationEvent;

import com.usic.SistemasActivosFijosUAP.config.sincronizacion.FileState;

import lombok.Builder;
import lombok.Getter;

@Getter
public class DbfChangeEvent extends ApplicationEvent{

    private final String tabla;
    private final String rutaDbf;
    private final FileState estadoAnterior;
    private final FileState estadoActual;
    private final LocalDateTime detectadoEn;

    @Builder
    public DbfChangeEvent(Object source, String tabla, String rutaDbf,
                          FileState estadoAnterior, FileState estadoActual) {
        super(source);
        this.tabla = tabla;
        this.rutaDbf = rutaDbf;
        this.estadoAnterior = estadoAnterior;
        this.estadoActual = estadoActual;
        this.detectadoEn = LocalDateTime.now();
    }
    
}
