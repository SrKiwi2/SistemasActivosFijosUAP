package com.usic.SistemasActivosFijosUAP.config.sincronizacion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Reserva capacidad para custodias aunque una sincronización DBF bloquee el pool general. */
@Configuration
public class SincronizacionSchedulerConfig {

    @Bean("taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler(
            @Value("${spring.task.scheduling.pool.size:6}") int tamañoPool) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Math.max(1, tamañoPool));
        scheduler.setThreadNamePrefix("scheduling-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean("custodiaTaskScheduler")
    public ThreadPoolTaskScheduler custodiaTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("custodia-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }

    @Bean("colaConfirmacionTaskScheduler")
    public ThreadPoolTaskScheduler colaConfirmacionTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("cola-confirmacion-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
