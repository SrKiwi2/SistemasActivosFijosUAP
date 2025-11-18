package com.usic.SistemasActivosFijosUAP.model.service;


import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.usic.SistemasActivosFijosUAP.model.dao.SyncControlRepository;
import com.usic.SistemasActivosFijosUAP.model.dto.interoperabilidad.SyncResult;
import com.usic.SistemasActivosFijosUAP.model.entity.SyncControl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SyncControlService {

    @PersistenceContext
    private EntityManager entityManager;

    private final SyncControlRepository syncControlRepository;
    
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

    /**
     * ✅ NUEVO MÉTODO SOBRECARGADO - Acepta información detallada
     * Usado por: Auxiliar, OrganismoFinanciero, GrupoContable, y futuras implementaciones
     * 
     * @param tabla Nombre de la tabla sincronizada
     * @param resultado Objeto con toda la información de sincronización
     */
    @Transactional
    public void registrarSincronizacion(String tabla, SyncResult resultado) {
        String sql = """
            UPDATE sync_control 
            SET ultima_sincronizacion = CURRENT_TIMESTAMP,
                registros_procesados = :procesados,
                registros_nuevos = :nuevos,
                registros_actualizados = :actualizados,
                duracion_ms = :duracion,
                estado = 'COMPLETADO',
                mensaje_error = NULL
            WHERE tabla_nombre = :tabla
            """;
        
        entityManager.createNativeQuery(sql)
            .setParameter("tabla", tabla)
            .setParameter("procesados", resultado.getTotalLeidas())
            .setParameter("nuevos", resultado.getInsertados())
            .setParameter("actualizados", resultado.getActualizados())
            .setParameter("duracion", resultado.getDuracionMs())
            .executeUpdate();
    }

    /**
     * Obtiene la información de sincronización de una tabla
     */
    public SyncControl obtenerInfoSincronizacion(String tablaNombre) {
        return syncControlRepository.findByTablaNombre(tablaNombre)
            .orElse(null);
    }

    
    /**
     * Registra un error en la sincronización
     */
    @Transactional
    public void registrarError(String tablaNombre, String mensajeError) {
        SyncControl sync = syncControlRepository.findByTablaNombre(tablaNombre)
            .orElseGet(() -> {
                SyncControl nuevo = new SyncControl();
                nuevo.setTablaNombre(tablaNombre);
                nuevo.setUltimaSincronizacion(LocalDateTime.now());
                return nuevo;
            });
        
        sync.setEstado("ERROR");
        sync.setMensajeError(mensajeError);
        
        syncControlRepository.save(sync);
    }
}
