package com.usic.SistemasActivosFijosUAP.model.service;


import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SyncControlService {

    @PersistenceContext
    private EntityManager entityManager;
    
    public LocalDateTime getUltimaSincronizacion(String tabla) {
        String sql = "SELECT ultima_sincronizacion FROM sync_control WHERE tabla_nombre = :tabla";
        return ((Timestamp) entityManager.createNativeQuery(sql)
            .setParameter("tabla", tabla)
            .getSingleResult())
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime();
    }
    
    @Transactional
    public void registrarSincronizacion(String tabla, int procesados, int nuevos, int actualizados, long duracionMs) {
        String sql = """
            UPDATE sync_control 
            SET ultima_sincronizacion = CURRENT_TIMESTAMP,
                registros_procesados = :procesados,
                registros_nuevos = :nuevos,
                registros_actualizados = :actualizados,
                duracion_ms = :duracion,
                estado = 'COMPLETADO'
            WHERE tabla_nombre = :tabla
            """;
        
        entityManager.createNativeQuery(sql)
            .setParameter("tabla", tabla)
            .setParameter("procesados", procesados)
            .setParameter("nuevos", nuevos)
            .setParameter("actualizados", actualizados)
            .setParameter("duracion", duracionMs)
            .executeUpdate();
    }
}
