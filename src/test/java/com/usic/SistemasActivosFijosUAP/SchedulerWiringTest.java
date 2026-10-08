package com.usic.SistemasActivosFijosUAP;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.task.TaskExecutor;

import com.usic.SistemasActivosFijosUAP.componet.AsyncConfig;
import com.usic.SistemasActivosFijosUAP.config.sincronizacion.SincronizacionSchedulerConfig;

class SchedulerWiringTest {

    @Test
    void importacionesUsanElEjecutorDeTrabajoAunqueExistanTresSchedulers() {
        try (AnnotationConfigApplicationContext contexto = new AnnotationConfigApplicationContext()) {
            contexto.register(AsyncConfig.class, SincronizacionSchedulerConfig.class);
            contexto.refresh();

            assertSame(contexto.getBean("syncTaskExecutor"), contexto.getBean(TaskExecutor.class));
        }
    }
}
